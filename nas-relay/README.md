# nas-relay — NAS용 Wake-on-LAN 중계 서버

- 폰의 WoL 신호는 Tailscale 터널을 넘지 못한다(매직패킷은 LAN 브로드캐스트). 24시간 켜진 NAS가 대신 쏜다.
- 폰이 Tailscale로 NAS에 `POST /wake`를 보내면, NAS가 같은 LAN의 PC에 매직패킷을 3회 보낸다.
- 파이썬 표준 라이브러리만 쓰는 단일 파일(`relay.py`)이다. 상태 확인(`/status`)과 종료(`/shutdown`)는 선택 기능.

## 실행 방법

먼저 설정 파일을 만든다.

```sh
cp .env.example .env
# RELAY_TOKEN(openssl rand -hex 32), RELAY_BIND(NAS Tailscale IP), PC_MAC, PC_HOST를 채운다
```

### 1. Docker compose (권장)

```sh
docker compose up -d --build
docker compose logs -f
```

`network_mode: host`는 필수다. 브릿지 네트워크에서는 UDP 브로드캐스트가 LAN에 나가지 않는다.

### 2. NAS에서 python3 직접 실행

Python 3.10 이상이 필요하다.

```sh
set -a; . ./.env; set +a
python3 relay.py
```

### 3. systemd 유닛 (`/etc/systemd/system/nas-relay.service`)

`relay.py`와 `.env`를 `/opt/nas-relay/`에 두었다고 가정한다.

```ini
[Unit]
Description=nas-relay (Wake-on-LAN)
After=network-online.target tailscaled.service
Wants=network-online.target

[Service]
ExecStart=/usr/bin/python3 -u /opt/nas-relay/relay.py
EnvironmentFile=/opt/nas-relay/.env
Restart=on-failure
RestartSec=5
NoNewPrivileges=yes

[Install]
WantedBy=multi-user.target
```

```sh
sudo systemctl daemon-reload && sudo systemctl enable --now nas-relay
```

부팅 직후 Tailscale IP가 아직 없으면 바인딩이 실패하고 종료한다. `Restart=on-failure`(Docker는 `restart: unless-stopped`)가 다시 시도한다.

## 설정 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `RELAY_TOKEN` | (필수) | 베어러 토큰. 32바이트 난수. 예시 값 그대로는 시작 거부 |
| `RELAY_BIND` | `127.0.0.1` | 듣는 주소. **NAS의 Tailscale IP(100.x)** 로 설정 |
| `RELAY_PORT` | `8585` | 듣는 포트 |
| `PC_MAC` | (필수) | PC MAC. `AA:BB:CC:DD:EE:FF` / `AA-BB-…` / `aabbccddeeff` |
| `WOL_BROADCAST` | `255.255.255.255` | 매직패킷 목적지 주소 |
| `WOL_PORT` | `9` | 매직패킷 UDP 포트 |
| `WOL_REPEAT` | `3` | 전송 횟수(1~20) |
| `PC_HOST` | (없음) | `/status` 대상 PC의 Tailscale/LAN IP. 없으면 `/status`는 400 |
| `SUNSHINE_PORT` | `47989` | `/status`가 TCP 연결을 시도할 포트(타임아웃 1.5초) |
| `ALLOW_CIDRS` | `100.64.0.0/10,127.0.0.0/8` | 허용 클라이언트 IP 대역(쉼표 구분). 그 밖은 403 |
| `SHUTDOWN_CMD` | (없음) | `/shutdown`이 셸로 실행할 명령(20초 제한). 없으면 `/shutdown`은 501 |

## 엔드포인트

| 요청 | 인증 | 응답 |
|---|---|---|
| `GET /health` | 없음 | `200 {"ok": true}` |
| `POST /wake` | 토큰 | `200 {"sent": 3, "mac": "AA:BB:…"}` |
| `GET /status` | 토큰 | `200 {"online": true/false}` (`PC_HOST` 없으면 400) |
| `POST /shutdown` | 토큰 | `200 {"ok": true, "returncode": 0}`, 명령 실패 502, 시간 초과 504, 미설정 501 |

그 밖의 경로·메서드는 404, 토큰 없음/불일치는 401, 허용 대역 밖은 403이다.

## 폰에서 확인

폰에 Tailscale을 켠 뒤 Termux 같은 터미널이나 PC에서 (`TOKEN`은 `.env`의 값):

```sh
curl http://100.x.x.x:8585/health
curl -H "Authorization: Bearer $TOKEN" -X POST http://100.x.x.x:8585/wake
curl -H "Authorization: Bearer $TOKEN" http://100.x.x.x:8585/status
```

## 보안 메모

- `RELAY_BIND`는 NAS의 Tailscale IP에만 맞춘다. `0.0.0.0`으로 열면 LAN 전체에 노출된다(시작 시 경고가 뜬다).
- 토큰은 `openssl rand -hex 32`로 만들고 `.env`와 앱 설정에만 둔다. 로그에는 토큰·헤더·쿼리 문자열이 남지 않는다. 토큰이 새면 바꾸고 재시작한다.
- `ALLOW_CIDRS`는 두 번째 방어선이다. 기본값(Tailscale + 루프백) 밖의 IP는 토큰이 맞아도 403이다.
- 공유기 포트포워딩·UPnP·DDNS는 쓰지 않는다. 이 서버를 인터넷에 직접 열지 않는다.
- `SHUTDOWN_CMD`는 셸 명령을 그대로 실행한다. `.env` 수정 권한이 곧 명령 실행 권한이므로 `.env`를 `chmod 600`으로 둔다. SSH 키는 이 용도 전용으로 만든다.

## 테스트

```sh
python -m pytest nas-relay/tests -q
```
