# "PC 원격 접속" 앱 명세 (초안 v0.2, 2026-11-02)

영상(민티저, https://youtu.be/31qcQLzcKkg)에서 보여 준 자작 앱을 이 레포에서 다시 만든다.
앱 하나가 Tailscale 연결 → PC 깨우기 → Sunshine 응답 대기 → Moonlight 실행을 버튼 하나로 묶는다.
영상은 앱의 내부 구조를 말하지 않았으므로, 아래 설계는 영상이 보여 준 화면과 설명(2초 폴링·NAS 경유
WoL·Sunshine 응답 감지·Moonlight 자동 실행)을 그대로 만족하는 가장 단순한 구성을 고른 것이다.

## 1. 기기와 역할

| 기기 | 상시 전원 | 설치물 | 역할 |
|---|---|---|---|
| 폰 (갤럭시 폴드8) | 사용 시 | Tailscale, Moonlight, **이 앱** | 조종·화면 수신·입력 송신 |
| 집 PC (Windows) | 평소 꺼짐 | Tailscale, Sunshine(서비스·자동 시작) | 게임 실행·화면 송출 |
| NAS | 24시간 | Tailscale, **중계 서버(`nas-relay/`)** | LAN 안에서 PC에 WoL 매직패킷 전송 |

폰의 WoL 신호가 집까지 못 가는 이유: 매직패킷은 LAN 브로드캐스트라 Tailscale 터널을 넘지 않는다.
그래서 24시간 켜진 NAS가 받아서 대신 쏜다.

## 2. 화면

영상의 화면 요소를 그대로 둔다.

| 요소 | 동작 |
|---|---|
| 상태 표시 (왼쪽 위) | `켜져 있음` / `꺼져 있음` / `깨우는 중…` / `Tailscale 끊김` |
| **PC 켜기** | NAS 중계 서버에 `POST /wake` → 폴링 계속 → Sunshine 응답 시 Moonlight 자동 실행(설정으로 끌 수 있음) |
| **화면 연결** | PC가 켜져 있을 때 Moonlight를 지정 호스트로 바로 연다 |
| **PC 끄기** | 아래 §4 방식 중 하나로 Windows 종료 |
| **키보드 버튼 켜기** | 물리 키보드가 없을 때 쓰는 가상 키보드 토글 — §5 미결 |
| 설정 (톱니) | §3 설정 항목 |

## 3. 설정 항목 (코드에 박지 않는다 — 앱 내부 저장소에만)

- PC: Tailscale IP 또는 MagicDNS 이름, MAC 주소, Sunshine HTTP 포트(기본 47989)
- NAS 중계: URL(`http://<nas-tailscale-ip>:8585`), 토큰
- Moonlight: 호스트 이름(또는 UUID), 열 앱 이름(기본 `Desktop`)
- 동작: 폴링 주기(기본 2초), 깨운 뒤 자동 연결(기본 켬), 깨우기 타임아웃(기본 90초)

## 4. 동작 상세

1. **상태 폴링**: 앱이 화면에 있을 때만 2초마다 `GET http://<pc>:47989/serverinfo`(Sunshine이 페어링 없이
   응답하는 엔드포인트)를 1.5초 타임아웃으로 친다. HTTP 200이면 `켜져 있음`. 연결 실패면 `꺼져 있음`.
   Tailscale VPN 인터페이스가 없으면 `Tailscale 끊김`으로 표시하고 Tailscale 앱 열기 버튼을 보여 준다.
2. **PC 켜기**: `POST /wake`(헤더 `Authorization: Bearer <토큰>`)를 NAS 중계에 보낸다. 중계는 LAN
   브로드캐스트(255.255.255.255:9)로 매직패킷을 3회 보낸다. 앱은 `깨우는 중…`으로 바꾸고 폴링을 계속한다.
   Sunshine 응답이 오면 `켜져 있음`으로 바꾸고, 자동 연결이 켜져 있으면 3단계를 실행한다. 타임아웃이면
   "PC가 응답하지 않습니다. BIOS WoL·빠른 시작 설정을 확인하세요." 안내.
3. **화면 연결**: Moonlight Android의 바로가기 진입점(`com.limelight/.ShortcutTrampoline`, exported)을 인텐트로 연다.
   extras는 전부 String이다: PC는 `UUID` 또는 `Name`(둘 중 하나 필수), 앱은 `AppId` 또는 `AppName`(기본 `Desktop`).
   `AppName`은 Moonlight가 캐시한 앱 목록에서 찾으므로 사용자가 앱 목록을 한 번 열어 둬야 한다(셋업 가이드 2-5).
   앱 지정을 빼면 그 PC의 앱 목록 화면이 열린다. Moonlight가 없거나 인텐트가 실패하면(`ActivityNotFoundException`)
   Play 스토어로 보낸다. 페어링은 Moonlight에서 한 번 손으로 한다 — 앱이 대신하지 않는다.
4. **PC 끄기** — 둘 중 하나를 고른다(기본은 A):
   - **A. Sunshine 앱 등록(추가 설치 0)**: Sunshine 웹 UI(`https://<pc>:47990`)의 Applications에
     이름 `PC 끄기`, 명령 `shutdown /s /t 5`인 앱을 추가한다. 앱은 Moonlight 인텐트(`AppName=PC 끄기`)로 그 앱을 실행한다.
     장점: SSH·에이전트가 필요 없다. 단점: Moonlight 화면이 잠깐 뜬다. (Sunshine REST API(47990)에는 앱 실행
     엔드포인트가 없고, 실행은 HTTPS 47984 `/launch`에 페어링 인증서가 필요하다 — 그래서 Moonlight를 거친다.)
   - **B. NAS 중계의 `POST /shutdown`**: NAS가 PC의 Windows OpenSSH 서버에 키로 접속해 `shutdown /s /t 0`을
     실행한다. 장점: 즉시·조용함. 단점: Windows OpenSSH 서버와 키 배포가 필요하다.
5. **키보드 버튼 켜기**: §5 미결 — Moonlight 자체의 화면 키보드(스트리밍 중 세 손가락 탭)를 안내하는
   방식이 가장 단순하다. 앱이 직접 키 입력을 PC로 보내려면 Moonlight 프로토콜을 다시 구현해야 하므로 하지 않는다.

### 4-1. 검증 메모 (2026-11-02, 공식 문서·소스 확인)

- Sunshine HTTP `/serverinfo`(47989)는 페어링·인증서 없이 응답한다. `state`·`hostname`·`uniqueid`가 들어 있고
  `mac`은 더미값(00:00:00:00:00:00)이라 MAC 자동 수집은 못 한다 → NAS `.env`에 손으로 적는다.
- Tailscale Windows는 기본 설정에서 로그인 전까지 연결되지 않는다 → **Run unattended** 필수. 이게 없으면
  WoL 뒤 폴링이 영원히 실패한다. 앱의 타임아웃 안내 문구에 이 항목을 넣는다.
- Synology DSM 7은 NAS→Tailscale 아웃바운드가 기본 비활성 → 중계의 `PC_HOST`는 PC LAN IP. 앱은 PC 상태를 NAS를
  거치지 않고 폰→PC Tailscale 직접 폴링으로 본다(중계의 `/status`는 보조).
- ShortcutTrampoline은 PC가 오프라인이고 MAC을 알면 스스로 WoL을 최대 10회 보내지만 같은 LAN에서만 의미가 있다.
  밖에서는 이 레포의 NAS 중계가 맡는다.

## 5. 미결 사항 (사용자 결정 필요)

| # | 질문 | 선택지 | 기본값(답이 없을 때) |
|---|---|---|---|
| 1 | NAS 종류 | Synology / QNAP / 직접 구성(리눅스) | Docker가 되는 NAS로 가정, 컨테이너 1개 |
| 2 | PC 끄기 방식 | §4-A Sunshine 앱 / §4-B SSH | A |
| 3 | 가상 키보드 | Moonlight 내장 안내 / 앱 자체 플로팅 키보드 | Moonlight 내장 |
| 4 | 앱 형태 | 단독 앱 / 이 레포의 "런처" 홈 화면 안의 타일 | 단독 앱(나중에 타일로 감쌀 수 있게 모듈화) |
| 5 | 폴링 범위 | 화면에 있을 때만 / 백그라운드 알림 | 화면에 있을 때만(배터리) |

## 6. 보안

- 모든 요청은 Tailscale 주소(100.64.0.0/10)로만 간다. 중계 서버는 NAS의 Tailscale 인터페이스에만 바인딩한다.
- 중계 토큰은 32바이트 난수. 앱 설정과 `nas-relay/.env`에만 있다. 레포에는 `.env.example`만 둔다.
- Sunshine 웹 UI(47990)는 Tailscale 밖에서 열리지 않는다(공유기 포트를 열지 않으므로). PIN 페어링은 Moonlight로만.

## 7. 기술 스택

- 앱: Kotlin, Jetpack Compose, minSdk 26, OkHttp(폴링·중계 호출), DataStore(설정). 라이브러리 최소.
- 중계: Python 3 표준 라이브러리만(`http.server` + `socket`), 단일 파일, Dockerfile + `docker-compose.yml`.
  테스트: 매직패킷 바이트 생성·토큰 검사·요청 라우팅을 pytest로.
- 빌드·실기기 검증은 로컬 Android Studio에서 한다. 원격 컨테이너에는 Android SDK가 없다.

## 8. 마일스톤

| 단계 | 산출물 | 완료 기준 |
|---|---|---|
| M0 손 셋업 | `docs/setup-guide.md` 절차 수행 | Tailscale 3대 연결, Moonlight로 손으로 접속 성공, NAS SSH에서 WoL로 PC가 켜짐 |
| M1 중계 | `nas-relay/` | 폰 브라우저에서 토큰 붙여 `POST /wake` → PC 켜짐, pytest 녹색 |
| M2 앱 핵심 | `app/` 상태 표시 + PC 켜기 + 화면 연결 | 영상과 같은 흐름: 버튼 → 켜짐 표시 → Moonlight 자동 실행 |
| M3 마무리 | PC 끄기, 키보드 토글, 설정 화면, 아이콘 | 영상 화면 요소 전부 동작 |
| M4 선택 | 홈 위젯, 폴드 커버 화면 대응 | 접은 상태에서도 켜기 가능 |
