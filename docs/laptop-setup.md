# 노트북 호스트 단계별 세팅 (갤럭시북5 Pro 360 + 폴드8)

목표: 노트북은 집에서 켜 둔 채 매일 새벽 자동 재부팅하고, 폰은 밖에서 Tailscale로 붙어 Moonlight로 화면을 받는다.
예상 소요 시간: 노트북 30분, 폰 10분.

## A. 노트북

1. **절전 끄기**
   - 설정 > 시스템 > 전원 및 배터리 > 화면, 절전 및 최대 절전 모드 시간 제한: 전원 연결 시 "절전" = 안 함 (화면 끄기는 5분이어도 된다).
   - `Win+R` → `powercfg.cpl` → "덮개를 닫으면 수행할 작업 선택" → 전원 사용 시 "아무 것도 안 함" → 변경 내용 저장.
2. **Wi-Fi 절전 끄기**: 장치 관리자 > 네트워크 어댑터 > Intel Wi-Fi > 속성 > 전원 관리 > "전원을 절약하기 위해 컴퓨터가 이 장치를 끌 수 있음" 해제. 공유기는 5GHz/6GHz 대역에 연결.
3. **배터리 보호**: Samsung Settings > 배터리 > 배터리 보호(충전 상한 85%) 켬.
4. **Tailscale**
   - tailscale.com/download 에서 Windows판 설치 → 로그인(폰과 같은 계정).
   - 작업 표시줄 Tailscale 아이콘 우클릭 > Preferences > **Run unattended** 체크.
   - login.tailscale.com/admin/machines 에서 이 노트북 `…` > **Disable key expiry**.
   - 노트북의 Tailscale IP(100.x.x.x)를 적어 둔다.
5. **Sunshine**
   - 가장 쉬운 방법: 관리자 PowerShell에서 `winget install --id LizardByte.Sunshine --exact`.
   - 또는 github.com/LizardByte/Sunshine/releases/latest 에서 **`Sunshine-Windows-AMD64-installer.msi`** 를 받아 실행(인텔 노트북은 AMD64, ARM64 아님). 서비스·방화벽 자동 등록.
   - 브라우저로 `https://localhost:47990` → 인증서 경고는 "계속" → 관리자 아이디·비밀번호 생성.
   - Applications > Add New: 이름 `PC 끄기`, Command `shutdown /s /t 5` 저장. 같은 방법으로 `재부팅`, `shutdown /r /t 5`.
6. **자동 로그인** (재부팅 후 바로 바탕화면이 떠야 스트리밍이 확실하다)
   - 설정 > 계정 > 로그인 옵션 > "보안 강화를 위해 Windows Hello 로그인만 허용" 끔.
   - `Win+R` → `netplwiz` → "사용자 이름과 암호를 입력해야 이 컴퓨터를 사용할 수 있음" 체크 해제 → 확인 → Microsoft 계정 비밀번호 입력.
   - 보안 트레이드오프: 노트북을 잃어버리면 바로 로그인된다. 들고 나갈 땐 체크를 다시 켠다.
7. **매일 자동 재부팅**: 시작 메뉴에서 "cmd" 우클릭 > 관리자 권한으로 실행 →
   `schtasks /create /tn "DailyReboot" /tr "shutdown /r /t 60" /sc daily /st 05:00 /ru SYSTEM`
   (취소: `schtasks /delete /tn "DailyReboot" /f`)
8. **업데이트 사용 시간**: 설정 > Windows 업데이트 > 고급 옵션 > 사용 시간을 주로 쓰는 시간대로.

## B. 폰 (폴드8)

1. **Tailscale**: Play 스토어 설치 → 같은 계정 로그인 → VPN 연결 허용. 다른 VPN 앱은 끈다.
2. **Moonlight**: Play 스토어 "Moonlight Game Streaming" 설치.
   - 집 Wi-Fi면 노트북이 자동으로 뜬다. 안 뜨면 오른쪽 위 `+` → 노트북 Tailscale IP 입력.
   - 노트북을 누르면 PIN 4자리가 뜬다 → 노트북 Sunshine 웹(`https://localhost:47990`) > PIN 탭에 입력.
   - 앱 목록이 나오면 `Desktop`을 한 번 열어 본다.
3. **화질 설정**(Moonlight 설정): 해상도 1080p, 60fps, 비트레이트 Wi-Fi 20Mbps / 5G 7Mbps. 해상도를 바꾸면 비트레이트가 초기화되니 마지막에 맞춘다.
4. **키보드·마우스**: 폰 블루투스 설정에서 페어링.

## C. 시험 (순서대로)

1. 집 Wi-Fi에서 Moonlight > Desktop 접속.
2. 폰 Wi-Fi 끄고 5G에서 접속.
3. Moonlight에서 `재부팅` 실행 → 1~2분 뒤 PC가 다시 온라인으로 바뀌는지, 바로 접속되는지.
4. 다음 날 아침 Moonlight에서 접속해 자동 재부팅 후에도 붙는지.

## 안 될 때

| 증상 | 확인 |
|---|---|
| 밖에서 PC가 오프라인 | 노트북 Tailscale 아이콘이 연결됨인지, Run unattended·key expiry 해제했는지 |
| 재부팅 후 안 붙음 | 자동 로그인 설정, Sunshine 서비스가 "자동" 시작인지(`services.msc` > Sunshine Service) |
| 화면이 검게 나옴 | 덮개를 완전히 닫으면 내장 화면이 꺼져 캡처가 안 될 수 있다 → 덮개를 조금 열거나 텐트 모양 |
| 끊김·지연 | Tailscale 앱에서 연결이 relay인지 direct인지, 비트레이트 낮추기, Wi-Fi 5GHz 확인 |
