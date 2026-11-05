"""nas-relay tests — stdlib HTTP server, fake services, no real network except loopback."""
from __future__ import annotations

import json
import logging
import socket
import subprocess
import sys
import threading
import urllib.error
import urllib.request
from contextlib import contextmanager
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import relay  # noqa: E402
from relay import Config, ConfigError, Services  # noqa: E402

TOKEN = "0123456789abcdef0123456789abcdef"
MAC = "AA:BB:CC:DD:EE:FF"
MAC_BYTES = bytes.fromhex("AABBCCDDEEFF")


# --------------------------------------------------------------------------- parse_mac

@pytest.mark.parametrize(
    "text",
    [
        "AA:BB:CC:DD:EE:FF",
        "aa:bb:cc:dd:ee:ff",
        "AA-BB-CC-DD-EE-FF",
        "aa-bb-cc-dd-ee-ff",
        "AABBCCDDEEFF",
        "aabbccddeeff",
        "  AA:BB:CC:DD:EE:FF\n",
    ],
)
def test_parse_mac_accepts_common_formats(text):
    assert relay.parse_mac(text) == MAC_BYTES


@pytest.mark.parametrize(
    "text",
    [
        "",
        "   ",
        "AA:BB:CC:DD:EE",  # too short
        "AA:BB:CC:DD:EE:FF:00",  # too long
        "AABBCCDDEEF",  # 11 hex digits
        "AABBCCDDEEFF00",  # 14 hex digits
        "GG:BB:CC:DD:EE:FF",  # not hex
        "AA:BB-CC:DD-EE:FF",  # mixed separators
        "AABB.CCDD.EEFF",  # unsupported dotted form
        "AA:BB:CC:DD:EE:F",  # short group
        "AAA:BB:CC:DD:EE:FF",  # long group
        "AA BB CC DD EE FF",  # spaces
    ],
)
def test_parse_mac_rejects_invalid(text):
    with pytest.raises(ValueError):
        relay.parse_mac(text)


def test_parse_mac_rejects_non_string():
    with pytest.raises(ValueError):
        relay.parse_mac(None)  # type: ignore[arg-type]


def test_format_mac_is_upper_colon():
    assert relay.format_mac(MAC_BYTES) == MAC


# --------------------------------------------------------------------------- magic_packet

def test_magic_packet_layout():
    pkt = relay.magic_packet(MAC_BYTES)
    assert len(pkt) == 102
    assert pkt[:6] == b"\xff" * 6
    assert pkt[6:] == MAC_BYTES * 16


@pytest.mark.parametrize("bad", [b"", b"\x00" * 5, b"\x00" * 7])
def test_magic_packet_rejects_wrong_length(bad):
    with pytest.raises(ValueError):
        relay.magic_packet(bad)


# --------------------------------------------------------------------------- client_allowed

def test_client_allowed_default_cidrs():
    cidrs = relay.DEFAULT_ALLOW_CIDRS
    assert relay.client_allowed("100.100.1.2", cidrs) is True
    assert relay.client_allowed("127.0.0.1", cidrs) is True
    assert relay.client_allowed("192.168.0.5", cidrs) is False
    assert relay.client_allowed("8.8.8.8", cidrs) is False


@pytest.mark.parametrize(
    "ip,expected",
    [
        ("100.64.0.0", True),
        ("100.127.255.255", True),
        ("100.63.255.255", False),
        ("100.128.0.0", False),
    ],
)
def test_client_allowed_cgnat_boundaries(ip, expected):
    assert relay.client_allowed(ip, ["100.64.0.0/10"]) is expected


def test_client_allowed_custom_and_garbage():
    assert relay.client_allowed("192.168.0.5", ["192.168.0.0/24"]) is True
    assert relay.client_allowed("not-an-ip", relay.DEFAULT_ALLOW_CIDRS) is False
    assert relay.client_allowed("", relay.DEFAULT_ALLOW_CIDRS) is False
    assert relay.client_allowed("100.100.1.2", []) is False


def test_client_allowed_ipv4_mapped_ipv6():
    assert relay.client_allowed("::ffff:100.100.1.2", relay.DEFAULT_ALLOW_CIDRS) is True
    assert relay.client_allowed("::ffff:192.168.0.5", relay.DEFAULT_ALLOW_CIDRS) is False


# --------------------------------------------------------------------------- Config.from_env

def test_from_env_defaults_and_normalization():
    cfg = Config.from_env({"RELAY_TOKEN": TOKEN, "PC_MAC": "aa-bb-cc-dd-ee-ff"})
    assert cfg.token == TOKEN
    assert cfg.mac == MAC
    assert cfg.bind == "127.0.0.1"
    assert cfg.port == 8585
    assert cfg.wol_broadcast == "255.255.255.255"
    assert cfg.wol_port == 9
    assert cfg.wol_repeat == 3
    assert cfg.pc_host is None
    assert cfg.sunshine_port == 47989
    assert cfg.allow_cidrs == ("100.64.0.0/10", "127.0.0.0/8")
    assert cfg.shutdown_cmd is None


def test_from_env_full():
    cfg = Config.from_env(
        {
            "RELAY_TOKEN": TOKEN,
            "RELAY_BIND": "100.101.102.103",
            "RELAY_PORT": "9000",
            "PC_MAC": "AABBCCDDEEFF",
            "WOL_BROADCAST": "192.168.0.255",
            "WOL_PORT": "7",
            "WOL_REPEAT": "5",
            "PC_HOST": "100.100.0.9",
            "SUNSHINE_PORT": "48000",
            "ALLOW_CIDRS": "100.64.0.0/10, 192.168.0.0/24",
            "SHUTDOWN_CMD": "ssh pc shutdown /s /t 0",
        }
    )
    assert cfg.bind == "100.101.102.103"
    assert cfg.port == 9000
    assert cfg.mac == MAC
    assert cfg.wol_broadcast == "192.168.0.255"
    assert cfg.wol_port == 7
    assert cfg.wol_repeat == 5
    assert cfg.pc_host == "100.100.0.9"
    assert cfg.sunshine_port == 48000
    assert cfg.allow_cidrs == ("100.64.0.0/10", "192.168.0.0/24")
    assert cfg.shutdown_cmd == "ssh pc shutdown /s /t 0"


def test_from_env_empty_values_count_as_unset():
    cfg = Config.from_env(
        {
            "RELAY_TOKEN": TOKEN,
            "PC_MAC": MAC,
            "RELAY_PORT": "",
            "PC_HOST": "",
            "ALLOW_CIDRS": "",
            "SHUTDOWN_CMD": "  ",
        }
    )
    assert cfg.port == 8585
    assert cfg.pc_host is None
    assert cfg.allow_cidrs == relay.DEFAULT_ALLOW_CIDRS
    assert cfg.shutdown_cmd is None


@pytest.mark.parametrize("env", [{"PC_MAC": MAC}, {"PC_MAC": MAC, "RELAY_TOKEN": ""}, {"PC_MAC": MAC, "RELAY_TOKEN": "   "}])
def test_from_env_requires_token(env):
    with pytest.raises(ConfigError, match="RELAY_TOKEN"):
        Config.from_env(env)


@pytest.mark.parametrize("env", [{"RELAY_TOKEN": TOKEN}, {"RELAY_TOKEN": TOKEN, "PC_MAC": ""}])
def test_from_env_requires_mac(env):
    with pytest.raises(ConfigError, match="PC_MAC"):
        Config.from_env(env)


def test_from_env_rejects_example_placeholder_token():
    with pytest.raises(ConfigError, match="RELAY_TOKEN"):
        Config.from_env({"RELAY_TOKEN": "change-me-32-bytes-hex", "PC_MAC": MAC})


@pytest.mark.parametrize(
    "key,value",
    [
        ("PC_MAC", "nope"),
        ("RELAY_PORT", "abc"),
        ("RELAY_PORT", "70000"),
        ("WOL_PORT", "0"),
        ("WOL_REPEAT", "0"),
        ("WOL_REPEAT", "x"),
        ("SUNSHINE_PORT", "-1"),
        ("RELAY_BIND", "not an ip"),
        ("WOL_BROADCAST", "pc.local"),
        ("ALLOW_CIDRS", "100.64.0.0/10,garbage"),
    ],
)
def test_from_env_rejects_bad_values(key, value):
    env = {"RELAY_TOKEN": TOKEN, "PC_MAC": MAC, key: value}
    with pytest.raises(ConfigError, match=key):
        Config.from_env(env)


def test_config_error_is_value_error():
    assert issubclass(ConfigError, ValueError)


def test_config_repr_hides_token():
    cfg = Config(token=TOKEN, mac=MAC)
    assert TOKEN not in repr(cfg)


def test_from_env_reads_os_environ_by_default(monkeypatch):
    monkeypatch.setenv("RELAY_TOKEN", TOKEN)
    monkeypatch.setenv("PC_MAC", MAC)
    assert Config.from_env().mac == MAC


def test_main_exits_nonzero_on_bad_config(monkeypatch, caplog):
    monkeypatch.delenv("RELAY_TOKEN", raising=False)
    monkeypatch.setenv("PC_MAC", MAC)
    with caplog.at_level(logging.ERROR):
        assert relay.main() != 0
    assert "RELAY_TOKEN" in caplog.text


# --------------------------------------------------------------------------- default services

def test_default_send_wol_sends_repeat_packets_to_udp_target():
    rx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx.bind(("127.0.0.1", 0))
    rx.settimeout(2)
    try:
        cfg = Config(
            token=TOKEN,
            mac=MAC,
            wol_broadcast="127.0.0.1",
            wol_port=rx.getsockname()[1],
            wol_repeat=3,
        )
        assert relay.default_send_wol(cfg) == 3
        packets = [rx.recvfrom(2048)[0] for _ in range(3)]
    finally:
        rx.close()
    assert all(p == relay.magic_packet(MAC_BYTES) for p in packets)


def test_default_probe_true_when_port_open_false_when_closed():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.bind(("127.0.0.1", 0))
    srv.listen(1)
    port = srv.getsockname()[1]
    try:
        assert relay.default_probe(Config(token=TOKEN, mac=MAC, pc_host="127.0.0.1", sunshine_port=port)) is True
    finally:
        srv.close()
    # the port is closed now
    assert relay.default_probe(Config(token=TOKEN, mac=MAC, pc_host="127.0.0.1", sunshine_port=port)) is False


def test_default_probe_false_without_pc_host():
    assert relay.default_probe(Config(token=TOKEN, mac=MAC)) is False


def test_default_run_shutdown_returns_exit_code():
    cfg = Config(token=TOKEN, mac=MAC, shutdown_cmd="exit 3")
    assert relay.default_run_shutdown(cfg) == 3


def test_default_run_shutdown_uses_shell_and_20s_timeout(monkeypatch):
    seen = {}

    def fake_run(cmd, **kwargs):
        seen["cmd"] = cmd
        seen.update(kwargs)
        return subprocess.CompletedProcess(cmd, 0)

    monkeypatch.setattr(relay.subprocess, "run", fake_run)
    cfg = Config(token=TOKEN, mac=MAC, shutdown_cmd="echo hi")
    assert relay.default_run_shutdown(cfg) == 0
    assert seen["cmd"] == "echo hi"
    assert seen["shell"] is True
    assert seen["timeout"] == 20


def test_services_defaults_are_the_real_implementations():
    s = Services()
    assert s.send_wol is relay.default_send_wol
    assert s.probe is relay.default_probe
    assert s.run_shutdown is relay.default_run_shutdown


# --------------------------------------------------------------------------- HTTP level

class Recorder:
    """Fake Services that records calls."""

    def __init__(self, online=True, shutdown_rc=0, wol_exc=None, shutdown_exc=None):
        self.wol_calls = []
        self.probe_calls = []
        self.shutdown_calls = []
        self.online = online
        self.shutdown_rc = shutdown_rc
        self.wol_exc = wol_exc
        self.shutdown_exc = shutdown_exc

    def send_wol(self, cfg):
        self.wol_calls.append(cfg)
        if self.wol_exc:
            raise self.wol_exc
        return cfg.wol_repeat

    def probe(self, cfg):
        self.probe_calls.append(cfg)
        return self.online

    def run_shutdown(self, cfg):
        self.shutdown_calls.append(cfg)
        if self.shutdown_exc:
            raise self.shutdown_exc
        return self.shutdown_rc

    def services(self) -> Services:
        return Services(send_wol=self.send_wol, probe=self.probe, run_shutdown=self.run_shutdown)


_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))  # ignore HTTP(S)_PROXY


@contextmanager
def running(cfg: Config, rec: Recorder):
    srv = relay.make_server(cfg, rec.services())
    thread = threading.Thread(target=srv.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{srv.server_address[1]}"
    finally:
        srv.shutdown()
        srv.server_close()
        thread.join(timeout=5)


def request(base, method, path, token=None, headers=None):
    hdrs = dict(headers or {})
    if token is not None:
        hdrs["Authorization"] = f"Bearer {token}"
    data = b"" if method in ("POST", "PUT", "PATCH") else None
    req = urllib.request.Request(base + path, data=data, method=method, headers=hdrs)
    try:
        with _OPENER.open(req, timeout=5) as resp:
            status, raw, ctype = resp.status, resp.read(), resp.headers.get("Content-Type")
    except urllib.error.HTTPError as e:
        status, raw, ctype = e.code, e.read(), e.headers.get("Content-Type")
    return status, json.loads(raw.decode()), ctype


def make_cfg(**over) -> Config:
    base = dict(token=TOKEN, mac=MAC, port=0, pc_host="100.100.0.9")
    base.update(over)
    return Config(**base)


@pytest.fixture
def rec():
    return Recorder()


@pytest.fixture
def base(rec):
    with running(make_cfg(), rec) as url:
        yield url


def test_health_ok_without_token(base):
    status, body, ctype = request(base, "GET", "/health")
    assert status == 200
    assert body == {"ok": True}
    assert ctype == "application/json"


def test_health_ignores_query_string(base):
    assert request(base, "GET", "/health?x=1")[0] == 200


def test_wake_without_token_is_401(base, rec):
    status, body, _ = request(base, "POST", "/wake")
    assert status == 401
    assert "error" in body
    assert rec.wol_calls == []


def test_wake_with_wrong_token_is_401(base, rec):
    status, _, _ = request(base, "POST", "/wake", token="wrong")
    assert status == 401
    assert rec.wol_calls == []


def test_wake_with_non_bearer_scheme_is_401(base, rec):
    status, _, _ = request(base, "POST", "/wake", headers={"Authorization": f"Basic {TOKEN}"})
    assert status == 401
    assert rec.wol_calls == []


def test_wake_with_non_ascii_token_is_401_not_500(base):
    assert request(base, "POST", "/wake", token="tök")[0] == 401


def test_wake_with_token_calls_service_once(base, rec):
    status, body, ctype = request(base, "POST", "/wake", token=TOKEN)
    assert status == 200
    assert body == {"sent": 3, "mac": MAC}
    assert ctype == "application/json"
    assert len(rec.wol_calls) == 1


def test_wake_reports_service_return_value():
    rec = Recorder()
    with running(make_cfg(wol_repeat=5), rec) as url:
        status, body, _ = request(url, "POST", "/wake", token=TOKEN)
    assert (status, body["sent"]) == (200, 5)


def test_wrong_method_is_404(base, rec):
    assert request(base, "GET", "/wake", token=TOKEN)[0] == 404
    assert request(base, "POST", "/health")[0] == 404
    assert request(base, "DELETE", "/wake", token=TOKEN)[0] == 404
    assert rec.wol_calls == []


def test_unknown_path_is_404(base):
    assert request(base, "GET", "/nope")[0] == 404
    assert request(base, "POST", "/nope", token=TOKEN)[0] == 404
    assert request(base, "GET", "/")[0] == 404
    assert request(base, "POST", "/wake/", token=TOKEN)[0] == 404


@pytest.mark.parametrize("online", [True, False])
def test_status_reports_probe_result(online):
    rec = Recorder(online=online)
    with running(make_cfg(), rec) as url:
        status, body, _ = request(url, "GET", "/status", token=TOKEN)
    assert status == 200
    assert body == {"online": online}
    assert len(rec.probe_calls) == 1


def test_status_requires_token(base, rec):
    assert request(base, "GET", "/status")[0] == 401
    assert rec.probe_calls == []


def test_status_without_pc_host_is_400(rec):
    with running(make_cfg(pc_host=None), rec) as url:
        status, body, _ = request(url, "GET", "/status", token=TOKEN)
    assert status == 400
    assert "PC_HOST" in body["error"]
    assert rec.probe_calls == []


def test_shutdown_unset_is_501(base, rec):
    status, body, _ = request(base, "POST", "/shutdown", token=TOKEN)
    assert status == 501
    assert "SHUTDOWN_CMD" in body["error"]
    assert rec.shutdown_calls == []


def test_shutdown_requires_token_even_when_enabled(rec):
    with running(make_cfg(shutdown_cmd="x"), rec) as url:
        assert request(url, "POST", "/shutdown")[0] == 401
    assert rec.shutdown_calls == []


def test_shutdown_set_runs_command(rec):
    with running(make_cfg(shutdown_cmd="ssh pc shutdown"), rec) as url:
        status, body, _ = request(url, "POST", "/shutdown", token=TOKEN)
    assert status == 200
    assert body == {"ok": True, "returncode": 0}
    assert len(rec.shutdown_calls) == 1


def test_shutdown_nonzero_exit_is_reported_as_failure():
    rec = Recorder(shutdown_rc=255)
    with running(make_cfg(shutdown_cmd="ssh pc shutdown"), rec) as url:
        status, body, _ = request(url, "POST", "/shutdown", token=TOKEN)
    assert status == 502
    assert body == {"ok": False, "returncode": 255}


def test_shutdown_timeout_is_504():
    rec = Recorder(shutdown_exc=subprocess.TimeoutExpired("x", 20))
    with running(make_cfg(shutdown_cmd="sleep 99"), rec) as url:
        status, body, _ = request(url, "POST", "/shutdown", token=TOKEN)
    assert status == 504
    assert body["ok"] is False


def test_service_exception_is_500_without_leaking_details():
    rec = Recorder(wol_exc=OSError("secret-network-detail"))
    with running(make_cfg(), rec) as url:
        status, body, _ = request(url, "POST", "/wake", token=TOKEN)
    assert status == 500
    assert "secret-network-detail" not in json.dumps(body)


def test_disallowed_client_ip_gets_403_everywhere(rec):
    cfg = make_cfg(allow_cidrs=("10.0.0.0/8",))  # 127.0.0.1 is not allowed
    with running(cfg, rec) as url:
        assert request(url, "GET", "/health")[0] == 403
        assert request(url, "POST", "/wake", token=TOKEN)[0] == 403
        assert request(url, "GET", "/nope")[0] == 403
    assert rec.wol_calls == []


def test_logs_never_contain_token(base, caplog):
    with caplog.at_level(logging.DEBUG):
        request(base, "POST", "/wake", token=TOKEN)
        request(base, "POST", f"/wake?token={TOKEN}", token="wrong-but-logged-as-401")
        request(base, "GET", "/status", headers={"X-Secret": TOKEN})
    assert TOKEN not in caplog.text
    assert "Authorization" not in caplog.text


def test_make_server_returns_threading_http_server(rec):
    from http.server import ThreadingHTTPServer

    srv = relay.make_server(make_cfg(), rec.services())
    try:
        assert isinstance(srv, ThreadingHTTPServer)
    finally:
        srv.server_close()
