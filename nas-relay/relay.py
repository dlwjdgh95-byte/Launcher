#!/usr/bin/env python3
"""NAS Wake-on-LAN relay — a tiny HTTP server (stdlib only).

Runs on an always-on NAS next to the sleeping PC. The phone reaches it over
Tailscale, and the NAS sends the magic packet on the LAN (a Tailscale tunnel
does not carry LAN broadcasts).

Endpoints (JSON):
    GET  /health    no auth      -> {"ok": true}
    POST /wake      bearer token -> send the magic packet
    GET  /status    bearer token -> is Sunshine answering on the PC?
    POST /shutdown  bearer token -> run SHUTDOWN_CMD (501 when unset)
Every request is first checked against ALLOW_CIDRS (403 otherwise).
"""
from __future__ import annotations

import hmac
import ipaddress
import json
import logging
import os
import re
import signal
import socket
import subprocess
import sys
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Iterable, Mapping, Optional

log = logging.getLogger("nas-relay")

DEFAULT_ALLOW_CIDRS = ("100.64.0.0/10", "127.0.0.0/8")  # Tailscale (CGNAT) + loopback
PLACEHOLDER_TOKEN = "change-me-32-bytes-hex"  # value in .env.example, never accepted
SHUTDOWN_TIMEOUT_S = 20
PROBE_TIMEOUT_S = 1.5


# --------------------------------------------------------------------------- pure helpers

_MAC_PLAIN = re.compile(r"[0-9A-Fa-f]{12}")
_MAC_SEP = re.compile(r"[0-9A-Fa-f]{2}([:-])(?:[0-9A-Fa-f]{2}\1){4}[0-9A-Fa-f]{2}")


def parse_mac(text: str) -> bytes:
    """'AA:BB:CC:DD:EE:FF' | 'AA-BB-...' | 'aabbccddeeff' -> 6 bytes. Raises ValueError."""
    if not isinstance(text, str):
        raise ValueError("MAC must be a string")
    s = text.strip()
    if _MAC_PLAIN.fullmatch(s):
        return bytes.fromhex(s)
    if _MAC_SEP.fullmatch(s):  # one separator style throughout
        return bytes.fromhex(re.sub(r"[:-]", "", s))
    raise ValueError(f"invalid MAC address: {text!r}")


def format_mac(mac: bytes) -> str:
    return ":".join(f"{b:02X}" for b in mac)


def magic_packet(mac: bytes) -> bytes:
    """6 x 0xFF followed by the MAC repeated 16 times (102 bytes)."""
    if len(mac) != 6:
        raise ValueError("MAC must be 6 bytes")
    return b"\xff" * 6 + bytes(mac) * 16


def client_allowed(ip: str, cidrs: Iterable[str]) -> bool:
    """True when `ip` falls inside any of `cidrs`. Garbage input is denied."""
    try:
        addr = ipaddress.ip_address(ip)
        if isinstance(addr, ipaddress.IPv6Address) and addr.ipv4_mapped:
            addr = addr.ipv4_mapped
        for c in cidrs:
            net = ipaddress.ip_network(c, strict=False)
            if addr.version == net.version and addr in net:
                return True
    except ValueError:
        return False
    return False


# --------------------------------------------------------------------------- config

class ConfigError(ValueError):
    """Invalid or missing environment configuration."""


def _opt(env: Mapping[str, str], key: str) -> Optional[str]:
    value = (env.get(key) or "").strip()
    return value or None  # empty (e.g. `KEY=` in .env) counts as unset


def _port(env: Mapping[str, str], key: str, default: int) -> int:
    raw = _opt(env, key)
    if raw is None:
        return default
    try:
        value = int(raw)
    except ValueError:
        raise ConfigError(f"{key} must be an integer 1-65535, got {raw!r}") from None
    if not 1 <= value <= 65535:
        raise ConfigError(f"{key} must be 1-65535, got {value}")
    return value


def _ip(env: Mapping[str, str], key: str, default: str) -> str:
    raw = _opt(env, key) or default
    try:
        ipaddress.ip_address(raw)
    except ValueError:
        raise ConfigError(f"{key} must be an IP address, got {raw!r}") from None
    return raw


@dataclass(frozen=True)
class Config:
    token: str = field(repr=False)  # never print the token
    mac: str  # normalized AA:BB:CC:DD:EE:FF
    bind: str = "127.0.0.1"
    port: int = 8585
    wol_broadcast: str = "255.255.255.255"
    wol_port: int = 9
    wol_repeat: int = 3
    pc_host: Optional[str] = None
    sunshine_port: int = 47989
    allow_cidrs: tuple = DEFAULT_ALLOW_CIDRS
    shutdown_cmd: Optional[str] = None

    @classmethod
    def from_env(cls, env: Optional[Mapping[str, str]] = None) -> "Config":
        env = os.environ if env is None else env

        token = _opt(env, "RELAY_TOKEN")
        if token is None:
            raise ConfigError("RELAY_TOKEN is required (non-empty)")
        if token == PLACEHOLDER_TOKEN:
            raise ConfigError("RELAY_TOKEN is still the .env.example placeholder; generate a real one")

        raw_mac = _opt(env, "PC_MAC")
        if raw_mac is None:
            raise ConfigError("PC_MAC is required")
        try:
            mac = format_mac(parse_mac(raw_mac))
        except ValueError as e:
            raise ConfigError(f"PC_MAC: {e}") from None

        repeat_raw = _opt(env, "WOL_REPEAT") or "3"
        try:
            repeat = int(repeat_raw)
        except ValueError:
            raise ConfigError(f"WOL_REPEAT must be an integer 1-20, got {repeat_raw!r}") from None
        if not 1 <= repeat <= 20:
            raise ConfigError(f"WOL_REPEAT must be 1-20, got {repeat}")

        cidrs_raw = _opt(env, "ALLOW_CIDRS")
        cidrs = DEFAULT_ALLOW_CIDRS
        if cidrs_raw is not None:
            cidrs = tuple(c.strip() for c in cidrs_raw.split(",") if c.strip())
            for c in cidrs:
                try:
                    ipaddress.ip_network(c, strict=False)
                except ValueError:
                    raise ConfigError(f"ALLOW_CIDRS has an invalid entry: {c!r}") from None

        return cls(
            token=token,
            mac=mac,
            bind=_ip(env, "RELAY_BIND", "127.0.0.1"),
            port=_port(env, "RELAY_PORT", 8585),
            wol_broadcast=_ip(env, "WOL_BROADCAST", "255.255.255.255"),
            wol_port=_port(env, "WOL_PORT", 9),
            wol_repeat=repeat,
            pc_host=_opt(env, "PC_HOST"),
            sunshine_port=_port(env, "SUNSHINE_PORT", 47989),
            allow_cidrs=cidrs,
            shutdown_cmd=_opt(env, "SHUTDOWN_CMD"),
        )


# --------------------------------------------------------------------------- side effects

def default_send_wol(cfg: Config) -> int:
    """Broadcast the magic packet cfg.wol_repeat times; return how many were sent."""
    packet = magic_packet(parse_mac(cfg.mac))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        for _ in range(cfg.wol_repeat):
            sock.sendto(packet, (cfg.wol_broadcast, cfg.wol_port))
    return cfg.wol_repeat


def default_probe(cfg: Config) -> bool:
    """True when a TCP connect to PC_HOST:SUNSHINE_PORT succeeds within 1.5 s."""
    if not cfg.pc_host:
        return False
    try:
        with socket.create_connection((cfg.pc_host, cfg.sunshine_port), timeout=PROBE_TIMEOUT_S):
            return True
    except OSError:
        return False


def default_run_shutdown(cfg: Config) -> int:
    """Run SHUTDOWN_CMD in a shell; return its exit code (TimeoutExpired propagates)."""
    proc = subprocess.run(
        cfg.shutdown_cmd, shell=True, timeout=SHUTDOWN_TIMEOUT_S, capture_output=True
    )
    return proc.returncode


@dataclass(frozen=True)
class Services:
    """Side effects the handler uses; tests inject fakes."""

    send_wol: Callable[[Config], int] = default_send_wol
    probe: Callable[[Config], bool] = default_probe
    run_shutdown: Callable[[Config], int] = default_run_shutdown


# --------------------------------------------------------------------------- HTTP

class RelayHandler(BaseHTTPRequestHandler):
    server_version = "nas-relay"
    sys_version = ""
    timeout = 10  # seconds a client may stall on the socket

    # (method, path) -> (needs_auth, handler method name)
    ROUTES = {
        ("GET", "/health"): (False, "_health"),
        ("POST", "/wake"): (True, "_wake"),
        ("GET", "/status"): (True, "_status"),
        ("POST", "/shutdown"): (True, "_shutdown"),
    }

    # --- logging: one access line per request (client, method, path without query,
    # status). Headers and query strings are never logged.
    def log_request(self, code="-", size="-"):
        pass

    def log_message(self, format, *args):
        log.info("%s %s", self.client_address[0], format % args)

    # --- plumbing
    def _reply(self, status: int, body: dict, headers: Optional[dict] = None) -> None:
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)
        path = self.path.split("?", 1)[0][:100]
        log.info("%s %s %r -> %d", self.client_address[0], self.command, path, status)

    def _authorized(self) -> bool:
        header = self.headers.get("Authorization", "")
        scheme, _, supplied = header.partition(" ")
        if scheme.lower() != "bearer":
            return False
        expected = self.server.cfg.token.encode()
        return hmac.compare_digest(supplied.strip().encode(), expected)

    def _dispatch(self) -> None:
        cfg: Config = self.server.cfg
        if not client_allowed(self.client_address[0], cfg.allow_cidrs):
            return self._reply(403, {"error": "forbidden"})
        path = self.path.split("?", 1)[0].split("#", 1)[0]
        route = self.ROUTES.get((self.command, path))
        if route is None:
            return self._reply(404, {"error": "not found"})
        needs_auth, name = route
        if needs_auth and not self._authorized():
            return self._reply(401, {"error": "unauthorized"}, {"WWW-Authenticate": "Bearer"})
        try:
            status, body = getattr(self, name)(cfg, self.server.services)
        except subprocess.TimeoutExpired:
            return self._reply(504, {"ok": False, "error": "command timed out"})
        except Exception:  # never leak internals to the client
            log.exception("handler %s failed", name)
            return self._reply(500, {"error": "internal error"})
        self._reply(status, body)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = do_HEAD = do_OPTIONS = _dispatch

    # --- endpoints: (cfg, services) -> (status, body)
    def _health(self, cfg, svc):
        return 200, {"ok": True}

    def _wake(self, cfg, svc):
        sent = svc.send_wol(cfg)
        return 200, {"sent": sent, "mac": cfg.mac}

    def _status(self, cfg, svc):
        if not cfg.pc_host:
            return 400, {"error": "PC_HOST is not configured"}
        return 200, {"online": bool(svc.probe(cfg))}

    def _shutdown(self, cfg, svc):
        if not cfg.shutdown_cmd:
            return 501, {"error": "SHUTDOWN_CMD is not configured"}
        rc = svc.run_shutdown(cfg)
        if rc == 0:
            return 200, {"ok": True, "returncode": rc}
        return 502, {"ok": False, "returncode": rc}


class RelayServer(ThreadingHTTPServer):
    def __init__(self, cfg: Config, services: Services):
        self.cfg = cfg
        self.services = services
        if ":" in cfg.bind:
            self.address_family = socket.AF_INET6
        super().__init__((cfg.bind, cfg.port), RelayHandler)


def make_server(cfg: Config, services: Optional[Services] = None) -> ThreadingHTTPServer:
    return RelayServer(cfg, services or Services())


# --------------------------------------------------------------------------- entry point

def _stop(signum, frame):  # docker stop / systemd stop -> clean exit
    raise KeyboardInterrupt


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        cfg = Config.from_env()
        server = make_server(cfg)
    except ConfigError as e:
        log.error("config error: %s", e)
        return 2
    except OSError as e:  # e.g. the Tailscale IP is not up yet; let the supervisor retry
        log.error("cannot listen: %s", e)
        return 1

    log.info(
        "nas-relay listening bind=%s port=%d allow=%s shutdown=%s",
        cfg.bind, cfg.port, ",".join(cfg.allow_cidrs), "enabled" if cfg.shutdown_cmd else "disabled",
    )
    if cfg.bind in ("0.0.0.0", "::"):
        log.warning("bound to all interfaces; set RELAY_BIND to the NAS Tailscale IP (100.x)")

    signal.signal(signal.SIGTERM, _stop)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
