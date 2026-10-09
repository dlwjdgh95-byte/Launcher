# 흑백 런처

갤럭시 Z 폴드8(One UI 9, Android 17)용 개인 홈 앱입니다. 홈 화면에 앱 아이콘을 두지 않고, 시스템 화면 전체를 흑백으로 바꾼 뒤 **런처에 등록한 앱만** 검색으로 열 수 있게 합니다. 자주 하는 일은 텍스트 루틴 버튼 하나로 실행합니다(예: '집 가기'를 누르면 카카오맵이 현재 위치에서 집까지 길을 찾아 줌). 쓸데없이 폰을 여는 충동을 꺾는 마찰 장치이고, 완전히 가두는 앱은 아닙니다. 커버 화면과 메인 화면 모두 이 런처가 맡습니다.

## 기능

- **집중 세션(흑백)**: 커버·메인 화면 전체를 흑백으로 바꿉니다. 빠른 설정 등에서 흑백을 끄면 바로 다시 켭니다.
- **등록 앱만 검색**: 세션 중에는 등록한 앱만 검색 결과에 나오고 실행됩니다. 홈에는 앱 목록이 없습니다.
- **한국어 검색**: 초성(`ㅋㅋㅇ`), 입력 중인 글자, 영문 자판으로 친 한글(`zkzkdh`), 별칭(`카맵`), 패키지 이름으로 찾습니다.
- **루틴 버튼**: 앱 열기, 딥링크, 바로가기, 대기, 미디어 재생, 볼륨, 방해 금지, 손전등, 타이머, 알람을 순서대로 실행합니다. 자세한 형식은 [docs/ROUTINES.md](docs/ROUTINES.md)에 있습니다.
- **종료와 시작**: '종료'를 누르면 5초 뒤 원래 색으로 돌아가고 모든 앱을 쓸 수 있습니다. '시작'을 누르면 다시 세션이 켜집니다.

막지 않는 것: 알림 탭, 최근 앱, 음성 명령처럼 런처를 거치지 않고 앱이 열리는 경로는 막지 않습니다(접근성 서비스를 쓰지 않음). 스마트뷰와 화면 녹화는 컬러로 나갈 수 있습니다.

## 설치

처음 설치한다면 [docs/INSTALL.md](docs/INSTALL.md)의 단계별 안내를 따라 하세요. 요약하면 다음과 같습니다.

### 1. APK 준비

- **내려받기**: GitHub [Releases](../../releases/latest)에서 `monolauncher-<버전>.apk`, `install.sh`(macOS), `install.ps1`(Windows)을 같은 폴더에 받습니다.
- **직접 빌드**: JDK 21과 Android SDK(platform 37)가 있으면
  ```sh
  ./gradlew assembleDebug
  # 결과: app/build/outputs/apk/debug/app-debug.apk
  ```
  디버그 APK와 릴리스 APK는 서명 키가 달라서 서로 덮어 설치할 수 없습니다. 한 가지만 쓰세요.

### 2. PC 설치 스크립트

폰을 USB로 연결하고 USB 디버깅을 켠 뒤, 삼성 **자동 차단기**를 잠시 끄고 실행합니다.

```sh
# macOS / Linux
bash scripts/install.sh [APK 경로]
```

```powershell
# Windows (PowerShell)
powershell -ExecutionPolicy Bypass -File scripts\install.ps1 [APK 경로]
```

APK 경로를 생략하면 스크립트가 있는 폴더와 현재 폴더에서 가장 최근 `monolauncher*.apk`(저장소 안이라면 빌드 결과물도)를 찾습니다. 스크립트가 하는 일:

1. adb와 연결된 기기(정확히 1대)를 확인합니다.
2. `adb install -r -g`로 설치합니다(기존 데이터와 권한 유지).
3. 흑백 권한을 줍니다: `adb shell pm grant app.monolauncher android.permission.WRITE_SECURE_SETTINGS`
4. `dumpsys package`로 `granted=true`인지 확인합니다.
5. 기본 홈 앱 지정을 시도합니다. 안 되면 직접 지정하라고 알려 줍니다.

설치 전에 기기 상태만 보고 싶다면 `scripts/device-check.sh`(Windows는 `device-check.ps1`)를 실행하세요. 흑백 관련 설정 키, 루틴 대상 앱 설치 여부, 앱 바로가기 id, Android·One UI 버전을 보여 주며 설정은 바꾸지 않습니다.

## 처음 실행 체크리스트

- [ ] 홈 버튼을 누르면 흑백 런처가 뜬다. 폰을 접어 커버 화면에서도 확인한다.
- [ ] 커버·메인 화면 전체가 흑백이다. 아니라면 [문제 해결](#문제-해결)의 '흑백 권한'을 보세요.
- [ ] '종료'를 누르면 5초 뒤 색이 돌아오고, '시작'을 누르면 다시 흑백이 된다.
- [ ] 종료 상태에서 설정을 열어 등록 앱을 확인한다. 빠진 앱(교보eBook for 삼성, 밀리의서재 등)이 있으면 추가한다.
- [ ] 설정 > 루틴 변수에 집 좌표 `home_lat`, `home_lng`를 넣는다.
- [ ] '집 가기' 루틴을 눌러 카카오맵 길찾기가 열리는지 본다.
- [ ] 자동 차단기를 다시 켜고, USB 디버깅을 끈다.

## 종료와 시작

| 상태 | 화면 | 검색·실행 | 홈 화면 |
|---|---|---|---|
| 세션 중 | 흑백 | 등록 앱만 | 루틴 버튼, 검색, '종료' |
| 종료 | 원래 색 | 모든 앱 | 검색, 설정, '시작' |

- '종료'를 누르면 **5초 카운트다운**이 시작되고, 그동안 '취소'로 돌아갈 수 있습니다. 끝나면 세션을 시작하기 전의 색 설정으로 되돌립니다.
- 종료 상태는 '시작'을 누를 때까지 유지됩니다. 재부팅해도 마지막 상태가 그대로 이어집니다.
- 대기 시간이 늘어나거나 횟수 제한이 있지는 않습니다.
- 설정(등록 앱, 별칭, 루틴 변수, 루틴 편집)은 **종료 상태에서만** 열 수 있습니다. 세션 중에 바꿀 수 있으면 우회로가 되기 때문입니다.

## 설정

모두 종료 상태의 설정 화면에 있습니다.

- **등록 앱**: 세션 중 검색·실행할 수 있는 앱입니다. 처음에는 교보eBook for 삼성, 밀리의서재, YouTube Music, 카카오맵, Google Home을 기준으로 합니다. 기기에 없는 앱은 목록에 나오지 않습니다.
- **별칭**: 앱마다 검색어를 더 붙입니다. 예: 카카오맵에 `지도`, `길`. 영어 이름 앱 일부(YouTube Music → `유튜브 뮤직`, Google Home → `구글 홈`)는 한글 별칭이 기본으로 들어 있습니다.
- **루틴 변수**: 루틴 안의 `{이름}` 자리에 들어갈 값입니다. '집 가기'는 `home_lat`(위도), `home_lng`(경도)를 씁니다. 좌표는 구글 지도에서 집 위치를 길게 누르면 `37.5665, 126.9780`처럼 나옵니다.
- **루틴**: JSON으로 편집합니다. 형식과 예시는 [docs/ROUTINES.md](docs/ROUTINES.md)를 보세요.

## 앱을 지울 때 주의

**흑백 설정은 앱을 지운 뒤에도 남습니다.** 지우기 전에 먼저 런처에서 '종료'를 눌러 색을 되돌리세요. 이미 지웠다면 폰의 설정 > 접근성에서 색상 보정(회색조)을 끄거나, PC에서 다음을 실행합니다.

```sh
adb shell settings put secure accessibility_display_daltonizer_enabled 0
```

앱을 지우면 흑백 권한도 사라지므로, 다시 설치할 때는 설치 스크립트를 다시 실행해야 합니다.

## 문제 해결

**기기가 안 보이거나 설치가 막힘**
삼성 자동 차단기가 켜져 있으면 USB 명령과 사이드로드가 막힙니다. 설정 > 보안 및 개인정보 보호 > 자동 차단기를 잠시 끄고 다시 실행하세요. 폰에 뜨는 'USB 디버깅 허용' 창도 허용해야 합니다.

**흑백 권한이 없다고 나오거나 흑백이 켜지지 않음**
설치 스크립트를 다시 실행하거나 직접 권한을 줍니다.
```sh
adb shell pm grant app.monolauncher android.permission.WRITE_SECURE_SETTINGS
```
앱을 지웠다가 다시 설치했다면 권한을 반드시 다시 줘야 합니다.

**홈 버튼을 누르면 One UI 홈이 뜸 (기본 홈이 아님)**
폰 설정 > 애플리케이션 > 기본 앱 선택 > 홈 화면 앱에서 '흑백 런처'를 고르세요(메뉴 이름은 One UI 버전에 따라 조금 다를 수 있습니다). 기본 홈이 아니면 루틴의 바로가기 실행과 앱 연속 실행이 막힐 수 있습니다.

**업데이트 설치가 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`로 실패**
이미 설치된 앱과 서명 키가 다릅니다. 항상 같은 키로 서명된 APK(GitHub Releases의 APK끼리, 또는 직접 빌드한 디버그 APK끼리)를 쓰세요.

**루틴 단계가 '건너뜀'으로 나옴**
세션 중에는 등록하지 않은 앱을 여는 단계와 패키지를 지정하지 않은 딥링크는 건너뜁니다. 값을 넣지 않은 루틴 변수가 있으면 그 단계는 실패합니다. [docs/ROUTINES.md](docs/ROUTINES.md#세션-중-규칙)를 보세요.

## 개발

- 빌드와 테스트: `./gradlew --no-configuration-cache testDebugUnitTest assembleDebug` (JDK 21)
- CI(`.github/workflows/android.yml`): 모든 push와 pull request에서 단위 테스트를 돌리고 디버그 APK를 아티팩트로 올립니다.
- 릴리스: `v`로 시작하는 태그를 푸시하면 서명된 APK를 만들어 GitHub Release에 올립니다.
  ```sh
  git tag v0.1.0 && git push origin v0.1.0
  ```
  versionName은 태그에서 `v`를 뺀 값, versionCode는 워크플로 실행 번호라 항상 커집니다. Release에는 APK와 함께 `install.sh`, `install.ps1`이 올라갑니다.
  서명 키 Secrets가 없으면 릴리스 단계는 오류 메시지만 남기고 건너뜁니다.
- 서명 키: `scripts/make-keystore.sh`로 한 번 만들고, 출력되는 값을 저장소 Secrets(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)에 넣습니다. 키스토어는 오프라인에 백업하세요. 잃어버리면 업데이트 설치가 불가능합니다.
- 로컬에서 릴리스 빌드를 하려면 저장소 루트에 `keystore.properties`(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)를 두세요. git에는 올라가지 않습니다.

## 문서

- [docs/PLAN.md](docs/PLAN.md): 기획과 조사 결과, 확정된 결정(14장)
- [docs/ROUTINES.md](docs/ROUTINES.md): 루틴 JSON 레퍼런스
- [docs/INSTALL.md](docs/INSTALL.md): 처음 설치하는 방법(개발자가 아니어도 따라 할 수 있게)
