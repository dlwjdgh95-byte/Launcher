# 셋업 가이드: 폴드8로 집 PC 원격 게임 (민티저 영상 재현)

영상(https://youtu.be/31qcQLzcKkg, 7:20~11:26 구간)이 보여 준 구성을 그대로 만든다. 집 PC는 평소 꺼져 있고,
밖에서 폰 버튼 하나로 켜서 화면을 받아 게임하고, 다 쓰면 끈다. 공유기 설정은 건드리지 않는다.
기술 사실은 2026-11-02에 공식 문서·소스로 확인했고, 확인 못 한 것은 "미확인"으로 적었다.

## 0. 한눈에 보기

```
 [폰: 폴드8]  ──Tailscale──▶  [NAS, 24시간]  ──LAN 브로드캐스트(WoL)──▶  [집 PC, 꺼짐→켜짐]
   Tailscale                    Tailscale                                   Tailscale(무인 모드)
   Moonlight  ◀──Tailscale── 화면·소리 ◀── Sunshine(서비스) ◀─────────────────┘
   PC 원격 접속 앱(M2)          nas-relay(M1)                                 게임 실행
```

- 왜 NAS가 필요한가: Wake-on-LAN 매직패킷은 LAN 브로드캐스트라 Tailscale 터널을 넘지 못한다. 같은 LAN에
  늘 켜진 기기가 대신 쏴야 하고, 그 역할을 NAS가 한다.
- 왜 포트포워딩이 없는가: Tailscale이 폰·PC·NAS를 내 계정 안의 사설망으로 묶는다. 공유기 입장에서는 모두
  "나가는" 연결이라 열 포트가 없다. 영상의 비유로는 "경비실에 방문 예약을 미리 해 두는 것".

## 1. 준비물

### 하드웨어

| 품목 | 요구 조건 | 메모 |
|---|---|---|
| 폰 | 갤럭시 Z 폴드8 (안드로이드 폰이면 어떤 것이든 동작) | 국내 256GB 2,278,100원, 201g. 영상은 민트색 |
| 집 PC | Windows, **유선 랜**, WoL 지원 메인보드·랜카드 | 무선 랜은 저전력 상태에서 매직패킷을 못 받는다. 멀티탭 전원을 끄면 WoL 불가 |
| NAS | 24시간 가동, Docker 또는 python3 실행 가능, PC와 같은 LAN | 선정·구성은 `docs/nas-guide.md` (추천: Synology DS225+ + CMR 4TB×2) |
| 공유기 | 아무거나 | 설정 변경 없음 |
| 3단 접이식 블루투스 키보드 | 폰 거치대 일체형이면 짐이 하나 준다 | **추천 1순위 ProtoArc XK01**(3단, 숫자패드 포함 풀 배열, 접이식 스탠드, 3대 페어링, 약 300g, 오픈마켓 약 7만~9.5만 원 — 터치패드판 XK01 TP 약 8.7만~, 백라이트판 XK01 Plus 약 10.9만~). 해외 리뷰에서 "입력 누락 없음"이 확인된 유일한 3단이라 영상이 아쉬워한 "씹힘·작은 키"를 피한다. 가볍고 싼 대안 iClever IC-BK08(3단+터치패드, 약 220g, 약 5.7만 원, 스탠드 일체형 약 6.5만 원)은 RTINGS가 지연을 낮게 평가해 게임용으로는 2순위. 둘 다 국내 정식 AS는 미확인(2026-11-03) |
| 블루투스 마우스 | 아무거나 | 영상은 소형 마우스 |

### 소프트웨어 (전부 무료)

| 설치 위치 | 프로그램 | 역할 |
|---|---|---|
| 폰·PC·NAS | Tailscale | 세 기기를 내 계정의 사설망으로 연결 (Personal 플랜 무료) |
| PC | Sunshine (LizardByte) | 화면·소리를 실시간 영상으로 송출. Windows 설치 프로그램이 서비스로 깔아 자동 시작 |
| 폰 | Moonlight (`com.limelight`) | Sunshine 화면 수신, 키보드·마우스 전달 |
| NAS | `nas-relay/` (이 레포) | 폰 요청을 받아 PC에 WoL 전송 |
| 폰 | PC 원격 접속 앱 (이 레포, M2 예정) | 위 과정을 버튼 하나로 |

### 계정·요금

- Tailscale 계정 하나(구글 등 소셜 로그인). 세 기기를 같은 계정에 등록한다.
- 모바일 데이터: 영상 실측으로 5G 7Mbps에서 15분에 0.64GB다. 150GB면 약 58시간, 20GB면 약 8시간이다.
  영상의 "150GB 9,750원, 7개월째부터 6만 원" 요금제는 2026-11-02 모요에서 같은 조건을 찾지 못했다(미확인).
  가장 가까운 것은 LG U+망 5G 150GB가 페이백 포함 월 12,450원, 7개월 이후 62,700원이었다. 모요에서
  데이터 150GB로 거르고 "7개월 이후 요금"과 페이백 조건을 같이 본다.

## 2. 설치 순서

PC → NAS → 폰 순서다. 각 단계 끝의 확인을 통과한 뒤 다음으로 간다.

### 2-1. PC: Wake-on-LAN 준비

1. BIOS/UEFI: `ErP/EuP` **Disabled**, `Power On By PCI-E` **Enabled** (ASUS 기준 Advanced > APM). 보드에 따라
   `Wake on LAN`, `Resume by PCI-E Device`, `S4/S5 Wake on LAN`으로 불린다. `Deep Sleep`이 있으면 끈다.
2. Windows 장치 관리자 > 네트워크 어댑터 > 고급: `Wake on Magic Packet` 켬. 전원 관리 탭: "이 장치가 컴퓨터를
   대기 모드에서 해제할 수 있도록 허용" + "Magic Packet만 허용" 켬.
3. 제어판 전원 옵션 > "현재 사용할 수 없는 설정 변경" > **빠른 시작 켜기 해제**. (빠른 시작이 켜져 있으면 종료
   상태에서 WoL이 안 되는 경우가 많다.)
4. `ipconfig /all`로 유선 랜의 **물리적 주소(MAC)** 와 **IPv4 주소**를 적어 둔다. 공유기에서 이 PC의 IP를 고정(DHCP 예약)한다.

확인: 같은 LAN의 다른 기기(NAS SSH 등)에서 매직패킷을 보내 켜지는지 본다. Synology는 `synonet --wake <MAC> eth0`.

### 2-2. PC: Tailscale

1. Tailscale 설치·로그인.
2. **Run unattended(무인 실행) 켜기** — 기본 설정에서는 재부팅 후 사용자가 로그인할 때까지 Tailscale이 연결되지
   않는다. WoL로 켠 직후 폰이 PC를 찾으려면 필수다.
3. 자동 로그인(`netplwiz`) 설정을 권한다. Sunshine이 바탕화면을 잡으려면 로그인이 끝나 있어야 한다.
   (Sunshine 서비스가 로그인 화면을 스트리밍하는지는 미확인 — 자동 로그인이 가장 확실하다.)
4. PC의 Tailscale IP(100.x.x.x)와 MagicDNS 이름을 적어 둔다.

### 2-3. PC: Sunshine

1. Windows 설치 프로그램으로 설치(서비스 설치가 기본). 포터블 zip이면 `scripts/install-service.bat`을 실행한다.
2. `https://localhost:47990` 웹 UI에서 관리자 계정을 만든다. 이 UI는 LAN·Tailscale 밖에서 열리지 않는다.
3. Applications에 `Desktop`이 있는지 보고, **`PC 끄기` 앱을 추가**한다: 이름 `PC 끄기`, 명령 `shutdown /s /t 5`.
   (Sunshine의 REST API에는 앱을 실행하는 엔드포인트가 없다. 앱 실행은 페어링된 Moonlight만 할 수 있어,
   "PC 끄기" 버튼은 Moonlight로 이 앱을 여는 방식이 된다. `shutdown` 명령 예시는 공식 문서에 없으니 한 번 실기 확인.)
4. 포트 메모: HTTP 47989(`/serverinfo`, 페어링 없이 응답 — 앱의 "켜져 있음" 판정에 쓴다), HTTPS 47984, 웹 UI 47990,
   RTSP 48010, UDP 47998~48000. 설치 프로그램이 방화벽 규칙을 넣는다.

확인: PC에서 `curl http://localhost:47989/serverinfo`가 XML(`state`, `hostname`)을 돌려준다.

### 2-4. NAS: Tailscale + nas-relay

1. Tailscale 설치. Synology는 패키지 센터 버전이 더 이상 지원되지 않아 SPK를 수동 설치한다. QNAP은 App Center >
   Communications. DSM 7은 TUN 권한이 없어 **NAS에서 나가는 Tailscale 연결이 기본 비활성**이지만, 이 구성은
   폰→NAS(들어오는 연결)와 NAS→PC(LAN)만 쓰므로 영향이 없다.
2. `tailscale ip -4`로 NAS의 Tailscale IP를 적어 둔다.
3. `nas-relay/README.md`대로 `.env`를 만든다. `RELAY_BIND`=NAS Tailscale IP, `PC_MAC`=2-1의 MAC,
   `PC_HOST`=**PC의 LAN IP**(Synology DSM 7에서는 Tailscale IP로 가면 위 TUN 제약에 걸린다), `RELAY_TOKEN`=`openssl rand -hex 32`.
4. `docker compose up -d --build` (반드시 `network_mode: host` — 브릿지에서는 브로드캐스트가 LAN에 안 나간다).

확인: NAS에서 `curl http://127.0.0.1:8585/health` → `{"ok": true}`. PC를 끄고 `curl -H "Authorization: Bearer $TOKEN" -X POST http://127.0.0.1:8585/wake` → PC가 켜진다. 안 켜지면 `WOL_BROADCAST`를 `192.168.x.255` 같은 LAN 브로드캐스트로 바꿔 본다.

### 2-5. 폰: Tailscale + Moonlight + 입력 장치

1. Tailscale 설치·로그인, VPN 구성 승인. 안드로이드는 VPN을 하나만 켤 수 있으니 다른 VPN 앱은 끈다.
   Exit node는 쓰지 않는다(배터리 소모의 주원인).
2. Moonlight 설치 → PC 추가(2-2의 Tailscale IP) → PIN이 뜨면 Sunshine 웹 UI PIN 탭에 입력해 페어링.
3. **앱 목록을 한 번 연다.** 나중에 앱이 Moonlight를 앱 이름으로 열 때 이 캐시를 쓴다.
4. 설정 > Basic settings: 해상도 1080p, 60fps. 비트레이트는 영상 기준 Wi-Fi 20Mbps, 5G 7Mbps
   (Moonlight 기본값은 720p60 10Mbps, 1080p60 20Mbps. 해상도·fps를 바꾸면 비트레이트가 기본값으로 돌아가니 다시 맞춘다).
5. 키보드·마우스를 블루투스로 페어링한다. 키보드 뒤 받침대를 펴고 앞 거치대에 폰을 펼쳐 올린다.

확인: Wi-Fi에서 Moonlight로 `Desktop` 접속 → 성공하면 Wi-Fi를 끄고 5G에서 반복. 폰에서
`http://<PC Tailscale IP>:47989/serverinfo`를 브라우저로 열면 XML이 보여야 한다(앱의 상태 폴링과 같은 요청).

### 2-6. 폰: PC 원격 접속 앱 (M2, 이 레포에서 구현 예정)

설정 화면에 PC Tailscale IP·MAC, NAS 중계 URL·토큰, Moonlight 호스트 이름을 넣으면 끝. 명세는 `docs/app-spec.md`.
앱이 생기기 전에는 수동 순서로 쓴다: Tailscale 켬 → (PC 꺼져 있으면) 브라우저나 Termux로 `/wake` → 1분쯤 뒤
Moonlight에서 PC가 온라인으로 바뀌면 접속.

### 2-7. NAS 없이 PC를 켜는 방법 (2026-11-05 추가)

NAS의 역할은 "LAN 안에서 매직패킷을 대신 쏘는 것"뿐이다. 그 역할을 다른 것으로 바꾸면 NAS가 필요 없다.

| 방법 | 비용 | 포트 개방 | 장단점 |
|---|---|---|---|
| **A. PC 상시 켜두기** | 전기 월 5천~1만 원(유휴 40~70W) | 0 | 장비·설정 0. 켜기·끄기 버튼이 필요 없다. 소음·전기만 감수 |
| **B. 스마트플러그 + BIOS "AC 복구 시 전원 켜기"** | 2~3만 원 | 0 | 갤럭시 SmartThings 앱·루틴으로 켠다. 끄기는 Sunshine `PC 끄기` 앱으로 종료한 뒤 플러그 OFF. 켜진 채로 플러그를 끄면 안 된다(SSD·OS 손상 위험). 앱은 SmartThings API로 호출 가능 |
| **C. 공유기 자체 WoL(ipTIME·ASUS 앱)** | 0 | 공유기 원격관리 포트 필요 | 영상의 "원래 방식". 공유기 관리 화면이 인터넷에 노출되는 게 약점. ASUS는 Instant Guard VPN 경유로 완화 |
| **D. Tailscale 되는 공유기(GL.iNet Flint 2·Beryl AX, OpenWrt)** | 7~15만 원(공유기 교체) | 0 | 공유기에서 Tailscale + `etherwake`. 중계 서버를 공유기가 맡는다. 네트워크 전체가 Tailscale 안으로 들어와 구조가 가장 깔끔 |
| **E. JetKVM·PiKVM(ATX 전원 제어)** | 약 10만 원 | 0 (Tailscale 내장) | 전원 버튼을 물리적으로 누르고 BIOS 화면까지 보인다. PC가 멈춰도 복구 가능. 가장 강력하지만 PC 뒤에 장비 하나 추가 |
| **F. 남는 안드로이드 폰(Termux) 또는 라즈베리파이 제로 2 W** | 0~4만 원 | 0 | NAS 대신 `nas-relay`를 돌리는 초소형 중계. Termux는 배터리 최적화 해제·wake lock이 필요해 손이 간다 |

IP-KVM(E) 비교 (2026-11-05, 다나와 국내 판매분 기준):

| 제품 | 본체 | ATX 전원 보드 | Tailscale | 메모 |
|---|---|---|---|---|
| **GL.iNet Comet GL-RM1** | 176,800원(11번가, 쿠팡 광고 159,500원) | 42,300원(별매) | 관리 화면 Apps Center에 내장 | 2K60, 1위. 국내 합계 약 22만 원. **알리 GL.iNet 공식 스토어 본체+ATX 번들 약 14.1만 원(2026-11-05, 공식 번들가 $90.90과 일치)** — $150 이하라 관세 없음, 개인통관고유부호 필요. GoodCloud는 끄고 Tailscale만 쓴다 |
| GL.iNet Comet PoE GL-RM1PE | 194,600원 | 42,300원 | 내장 | PoE 공유기·스위치가 있을 때만 의미 |
| JetKVM | 232,960원 | 61,170~104,830원(별매, 편차 큼) | 개발자 모드 + SSH로 공식 스크립트 설치(수동) | 오픈소스·완성도 좋지만 합계 30만 원대. 5만~8만 원대 "JetKVM" 매물은 TF/호환품이라 제외 |
| Sipeed NanoKVM Full/Cube | 9만~16만 원 | 내장 | 설치 필요, 기본 꺼짐 | 2025 펌웨어 보안 논란(이후 오픈소스화). 가장 싸지만 신뢰 감점 |
| PiKVM V4 Mini/Plus | 약 63만 원~ | 내장 | 공식 패키지 | 가장 성숙하지만 가정용엔 과함 |
| Openterface Mini-KVM | — | 없음 | 없음 | USB 직결 휴대용이라 원격 전원·네트워크 불가, 제외 |

고르는 기준: 가장 싸고 단순한 건 B(스마트플러그), "포트 0 개방 + 제대로"는 D 또는 E, 귀찮으면 A.
`nas-relay`의 `/wake`는 B·D·E·F 어디에 두어도 앱 쪽은 바뀌지 않는다(앱 명세 §4-2의 중계 URL만 바꾼다).

## 3. 전체 동작 순서 (완성 후)

1. 폰에서 `PC 켜기` → 폰이 Tailscale로 NAS `/wake` 호출 → NAS가 LAN에 매직패킷 3회.
2. PC 전원 인가 → Windows 부팅 → 자동 로그인 → Tailscale(무인 모드)·Sunshine 서비스 기동.
3. 앱이 2초마다 `GET /serverinfo`(47989) → 응답이 오면 `켜져 있음`.
4. 앱이 Moonlight를 인텐트로 열어 `Desktop` 스트리밍 시작. 키보드·마우스 입력은 PC로 간다.
5. 끝나면 `PC 끄기` → Moonlight로 Sunshine의 `PC 끄기` 앱 실행 → 5초 뒤 종료 → 앱 상태 `꺼져 있음`.

## 4. 데이터·배터리·비용 (영상 실측, 15분 게임)

| 접속 | 설정 | 데이터 | 배터리 | 비고 |
|---|---|---|---|---|
| Wi-Fi | 1080p 60fps 20Mbps | 2.21GB | -2.2%, 32℃ | 폰은 디코딩만 하므로 발열이 낮다 |
| 5G | 1080p 60fps 7Mbps | 0.64GB | (미언급) | 7Mbps 이론값은 0.79GB. 평균 약 5.7Mbps로 쓴 셈 |

- 월 데이터: 150GB ≈ 58시간, 20GB ≈ 8시간 (0.64GB/15분 기준).
- 5G에서 Tailscale이 직접 연결에 실패하면 DERP 릴레이를 타며 지연이 커진다. 품질이 나쁘면 Tailscale 앱에서
  연결 종류(direct/relay)를 확인한다.

## 5. 함정 목록

- **무인 모드 안 켬**: WoL로 켜진 PC가 로그인 전까지 Tailscale에 안 붙어 폰이 영원히 "꺼져 있음"을 본다. (2-2)
- **빠른 시작 켜짐·ErP 켜짐·무선 랜**: 종료 상태에서 매직패킷을 못 받는다. (2-1)
- **Docker 브릿지 네트워크**: 브로드캐스트가 LAN에 안 나간다. `network_mode: host`. (2-4)
- **Synology DSM 7 TUN**: NAS→Tailscale 아웃바운드 불가. `PC_HOST`는 LAN IP. (2-4)
- **Sunshine HTTP `/serverinfo`의 `mac`은 더미값(00:00:…)**: 앱이 MAC을 자동으로 못 얻는다. NAS `.env`에 손으로 적는다.
- **Moonlight 앱 이름 캐시**: 앱 목록을 한 번도 안 열었으면 이름으로 실행이 실패한다. (2-5)
- **다른 VPN 앱**: 안드로이드는 VPN 하나만. Tailscale만 켠다.
- **PC 자동 로그인**: 보안상 꺼 두고 싶다면 Sunshine의 로그인 화면 스트리밍 가능 여부를 먼저 실기로 확인한다(미확인).

## 6. 영상과 이 구성의 차이

- 영상은 "원래는 공유기 앱으로 PC를 켰다"고 했고, 자작 앱은 NAS를 거친다. 이 레포도 NAS 중계(`nas-relay/`)를 쓴다.
  공유기가 Tailscale을 지원하지 않는 일반 제품이라는 전제다.
- 영상은 앱 내부(폰→NAS 신호 방식, PC 끄기 방식, 가상 키보드)를 설명하지 않았다. 이 문서의 선택(HTTP 중계·토큰,
  Sunshine 앱으로 종료, Moonlight 내장 키보드)은 `docs/app-spec.md` §4~5에 근거를 적었다.
- 영상의 게임 이름("귀모자")과 키보드 모델은 자막에서 확인되지 않는다.

## 출처

- Sunshine 포트·`/serverinfo`·REST API·앱 설정: https://github.com/LizardByte/Sunshine (src/nvhttp.cpp, src/process.cpp, docs/api.md, docs/getting_started.md)
- Moonlight Android 바로가기 진입점·기본 설정: https://github.com/moonlight-stream/moonlight-android (ShortcutTrampoline.java, PreferenceConfiguration.java)
- Tailscale: Synology https://tailscale.com/kb/1131/synology · QNAP https://tailscale.com/kb/1273/qnap · Android https://tailscale.com/kb/1078/android-vpn · 무인 모드 https://tailscale.com/kb/1088/run-unattended · MagicDNS https://tailscale.com/kb/1081/magicdns · 다른 VPN https://tailscale.com/docs/reference/faq/other-vpns · 배터리 https://tailscale.com/docs/reference/troubleshooting/mobile/battery-drains · 연결 종류 https://tailscale.com/kb/1257/connection-types
- WoL 설정: ASUS https://asus.com/support/FAQ/1048459 · Dell https://www.dell.com/support/kbdoc/en-za/000129137 · https://woshub.com/wake-on-lan-windows/ · https://en.wikipedia.org/wiki/Wake-on-LAN
- Synology WoL 발송: https://www.synoforum.com/threads/wake-up-wol-another-device-on-the-lan-from-a-synology-nas.5960/
- 폴드8 가격·사양: https://www.samsung.com/sec/smartphones/galaxy-z-fold8/specs/ · https://v.daum.net/v/20260722220127394
- 키보드 후보·요금제: 다나와 검색(아이클레버 접이식 키보드 / 폴더블 블루투스 키보드 거치대) · 모요 https://www.moyoplan.com/plans
