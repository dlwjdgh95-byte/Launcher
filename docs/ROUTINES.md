# 루틴 JSON 레퍼런스

루틴은 홈 화면의 텍스트 버튼입니다. 누르면 정해 둔 단계(step)를 위에서부터 차례로 실행합니다. 루틴은 **종료 상태**의 설정 > 루틴에서 JSON으로 편집합니다. 형식의 기준은 `app/src/main/java/app/monolauncher/routine/Routine.kt`입니다.

## 전체 구조

```json
{
  "version": 1,
  "routines": [
    {
      "id": "go_home",
      "label": "집 가기",
      "steps": [
        { "type": "deeplink", "uri": "kakaomap://route?ep={home_lat},{home_lng}&by={home_by}", "packageName": "net.daum.android.map" }
      ]
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `version` | 형식 버전. 지금은 `1`. |
| `routines` | 루틴 목록. 이 순서대로 홈에 버튼이 놓입니다. |
| `id` | 루틴마다 다른 이름. 영문·숫자·밑줄을 권장합니다. |
| `label` | 버튼에 보이는 글자. |
| `steps` | 실행할 단계 목록. 위에서부터 순서대로 실행합니다. |

작성 규칙:

- 모든 단계에는 `"type"`이 있어야 합니다. 필드 이름은 대소문자를 구분합니다(`packageName`이지 `package`가 아닙니다).
- 모르는 필드는 무시합니다. 하지만 **`type` 철자가 틀리거나 필수 필드가 빠지면 JSON 전체를 읽지 못합니다.**
- 숫자 필드(`ms`, `percent`, `seconds`, `hour`, `minute`)에는 따옴표를 붙이지 않습니다. `true`/`false`도 마찬가지입니다.

## 단계 종류

| `type` | 하는 일 | 필드 |
|---|---|---|
| [`launch`](#launch--앱-열기) | 앱 열기 | `packageName`, `alternatives`(선택) |
| [`deeplink`](#deeplink--링크로-열기) | 링크(URL, 앱 스킴) 열기 | `uri`, `packageName`(선택) |
| [`shortcut`](#shortcut--앱-바로가기) | 앱 바로가기 실행 | `packageName`, `shortcutId` |
| [`delay`](#delay--기다리기) | 기다리기 | `ms` |
| [`media`](#media--재생-제어) | 재생·일시정지·다음 곡 | `action` |
| [`volume`](#volume--미디어-볼륨) | 미디어 볼륨 | `percent` |
| [`dnd`](#dnd--방해-금지) | 방해 금지 켜기/끄기 | `on` |
| [`torch`](#torch--손전등) | 손전등 켜기/끄기 | `on` |
| [`timer`](#timer--타이머) | 타이머 설정 | `seconds`, `message`(선택) |
| [`alarm`](#alarm--알람) | 알람 설정 | `hour`, `minute`, `message`(선택) |
| [`home`](#home--런처로-돌아오기) | 런처로 돌아오기 | 없음 |

### launch — 앱 열기

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `packageName` | 문자열 | 예 | 열 앱의 패키지 이름 |
| `alternatives` | 문자열 목록 | 아니요 | `packageName`을 열 수 없을 때 차례로 시도할 다른 패키지(같은 앱의 갤럭시 스토어판·Play판 등) |

```json
{ "type": "launch", "packageName": "com.google.android.apps.youtube.music" }
```

같은 앱이 스토어마다 패키지가 다르면 `alternatives`에 나머지를 적어 두세요. 설치된 쪽이 열립니다. `packageName`부터 적힌 순서대로 시도해 처음 열린 앱에서 멈추고, 하나도 열리지 않으면 실패합니다.

```json
{ "type": "launch", "packageName": "com.kyobo.ebook.samsung", "alternatives": ["com.kyobo.ebook.common.b2c"] }
```

패키지 이름은 `scripts/device-check.sh`(Windows는 `device-check.ps1`)로 확인할 수 있습니다. 이 프로젝트에서 쓰는 앱:

| 앱 | 패키지 |
|---|---|
| 카카오맵 | `net.daum.android.map` |
| YouTube Music | `com.google.android.apps.youtube.music` |
| Google Home | `com.google.android.apps.chromecast.app` |
| 밀리의서재 | `kr.co.millie.millieshelf` (갤럭시 스토어판 `kr.co.millie.millieshelf.samsung`) |
| 교보eBook for 삼성 | `com.kyobo.ebook.samsung` (Play판 교보eBook은 `com.kyobo.ebook.common.b2c`) |

### deeplink — 링크로 열기

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `uri` | 문자열 | 예 | 열 링크. `https://…` 또는 `kakaomap://…` 같은 앱 스킴 |
| `packageName` | 문자열 | 아니요 | 링크를 열 앱. **세션 중에는 반드시 있어야 실행됩니다** |

```json
{ "type": "deeplink", "uri": "kakaomap://route?ep={home_lat},{home_lng}&by={home_by}", "packageName": "net.daum.android.map" }
```

자주 쓰는 링크:

| 하는 일 | `uri` | `packageName` |
|---|---|---|
| 현재 위치 → 목적지 길찾기 | `kakaomap://route?ep=위도,경도&by=publictransit` | `net.daum.android.map` |
| 출발지를 정한 길찾기 | `kakaomap://route?sp=위도,경도&ep=위도,경도&by=car` | `net.daum.android.map` |
| 길찾기 입력 화면 | `kakaomap://open?page=routeSearch` | `net.daum.android.map` |
| 밀리의서재 투데이 | `millieshelf://app?menu_id=viewfinder` | `kr.co.millie.millieshelf` |
| 위치 보기 | `kakaomap://look?p=위도,경도` | `net.daum.android.map` |
| YouTube Music 재생목록 | `https://music.youtube.com/playlist?list=재생목록ID` | `com.google.android.apps.youtube.music` |

카카오맵 `by` 값: `car`(자동차), `publictransit`(대중교통), `foot`(도보), `bicycle`(자전거). `sp`를 빼면 현재 위치에서 출발하는 것으로 보이지만 공식 문서에는 없어서 폰에서 확인이 필요합니다. 밀리의서재 링크는 공개 규약이 아니라 앱 업데이트로 바뀔 수 있습니다. 교보eBook은 링크가 없어 `launch`만 됩니다.

### shortcut — 앱 바로가기

앱 아이콘을 길게 누르면 나오는 바로가기(예: 특정 기기 제어, 최근 책)를 실행합니다.

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `packageName` | 문자열 | 예 | 바로가기를 가진 앱 |
| `shortcutId` | 문자열 | 예 | 바로가기 id |

```json
{ "type": "shortcut", "packageName": "com.google.android.apps.chromecast.app", "shortcutId": "바로가기id" }
```

- `shortcutId`는 `scripts/device-check.sh`의 '앱 바로가기' 항목에서 확인합니다. 앱을 한 번도 열지 않았다면 안 나올 수 있습니다.
- 흑백 런처가 **기본 홈 앱**이어야 실행됩니다.
- 앞 단계에서 다른 앱이 열려 있으면, 런처가 잠깐 앞으로 나온 뒤 바로가기를 엽니다(안드로이드가 뒤에 있는 앱의 바로가기 실행을 막기 때문).

### delay — 기다리기

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `ms` | 정수 | 예 | 기다릴 시간(밀리초). `2500` = 2.5초 |

```json
{ "type": "delay", "ms": 2500 }
```

### media — 재생 제어

지금 소리를 내고 있거나 마지막으로 재생한 앱에 미디어 버튼을 보냅니다. 세션 중에는 앞에서 앱을 여는 단계가 건너뛰어지거나 실패했으면 이 단계도 건너뜁니다([세션 중 규칙](#세션-중-규칙)).

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `action` | 문자열 | 예 | `play`, `pause`, `toggle`(재생/일시정지 전환), `next`, `previous` |

```json
{ "type": "media", "action": "play" }
```

### volume — 미디어 볼륨

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `percent` | 정수 | 예 | 0~100. 범위를 벗어나면 0 또는 100으로 맞춥니다 |

```json
{ "type": "volume", "percent": 40 }
```

### dnd — 방해 금지

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `on` | `true`/`false` | 예 | `true`: 중요 알림만 허용, `false`: 끄기 |

```json
{ "type": "dnd", "on": true }
```

이 단계는 **흑백 런처가 켠 방해 금지만** 켜고 끕니다. 사용자가 빠른 설정에서 켰거나 다른 앱·모드가 켠 방해 금지는 `"on": false`로 끌 수 없으며, 이때는 '다른 곳에서 켠 방해 금지는 끌 수 없습니다'라며 건너뜁니다(안드로이드 15부터 앱은 자기가 켠 방해 금지만 끌 수 있음).

'방해 금지 접근' 권한이 없으면 이 단계는 건너뜁니다. 폰 설정의 특별한 접근 > 방해 금지 권한에서 흑백 런처를 허용하거나(메뉴 위치는 One UI 버전에 따라 다를 수 있음), PC에서 다음을 실행하세요.

```sh
adb shell cmd notification allow_dnd app.monolauncher
```

### torch — 손전등

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `on` | `true`/`false` | 예 | 켜기/끄기 |

```json
{ "type": "torch", "on": true }
```

### timer — 타이머

시계 앱에 타이머를 맞춥니다(가능하면 시계 화면을 띄우지 않음).

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `seconds` | 정수 | 예 | 1~86400(24시간) |
| `message` | 문자열 | 아니요 | 타이머 이름 |

```json
{ "type": "timer", "seconds": 1800, "message": "독서 끝" }
```

### alarm — 알람

| 필드 | 형식 | 필수 | 설명 |
|---|---|---|---|
| `hour` | 정수 | 예 | 0~23 |
| `minute` | 정수 | 예 | 0~59 |
| `message` | 문자열 | 아니요 | 알람 이름 |

```json
{ "type": "alarm", "hour": 7, "minute": 30, "message": "기상" }
```

### home — 런처로 돌아오기

필드가 없습니다.

```json
{ "type": "home" }
```

## 변수 (자리표시자)

문자열 필드에 `{이름}`을 쓰면 실행할 때 설정 > 루틴 변수의 값으로 바뀝니다.

```json
{ "type": "deeplink", "uri": "kakaomap://route?ep={home_lat},{home_lng}&by={home_by}", "packageName": "net.daum.android.map" }
```

| 변수 | 쓰는 곳 | 예 |
|---|---|---|
| `home_lat` | '집 가기'의 도착지 위도 | `37.5665` |
| `home_lng` | '집 가기'의 도착지 경도 | `126.9780` |
| `home_by` | '집 가기'의 이동수단 (기본값 `publictransit`) | `car` |

- 이름은 영문자나 밑줄로 시작하고 영문자·숫자·밑줄만 씁니다. 필요한 변수는 설정 > 루틴 변수에서 직접 더 만들 수 있습니다(예: `work_lat`, `work_lng`).
- 쓸 수 있는 필드: `launch.packageName`, `launch.alternatives`(목록의 각 항목), `deeplink.uri`, `deeplink.packageName`, `shortcut.packageName`, `shortcut.shortcutId`, `timer.message`, `alarm.message`. 숫자 필드에는 쓸 수 없습니다.
- 값을 넣지 않은 변수가 있으면 그 단계는 '설정에서 'home_lat' 값을 입력해 주세요'라며 실패하고, 나머지 단계는 계속 실행됩니다.
- 집 좌표는 집에서 설정 > 루틴 변수의 **'현재 위치를 집으로 저장'** 을 누르면 자동으로 들어갑니다(위치 권한 필요). 직접 넣으려면 구글 지도에서 위치를 길게 누르면 `37.5665, 126.9780`처럼 나옵니다. 앞이 위도, 뒤가 경도입니다.

## 세션 중 규칙

세션 중(흑백)에는 등록한 앱만 열 수 있다는 규칙이 루틴에도 똑같이 적용됩니다.

- `launch`, `deeplink`, `shortcut` 단계가 **등록하지 않은 앱**을 열려고 하면 그 단계를 건너뜁니다.
- `alternatives`가 있는 `launch`는 `packageName`과 `alternatives` 중 **등록한 앱만** 적힌 순서대로 시도합니다. 하나도 등록하지 않았으면 건너뜁니다.
- `packageName`이 없는 `deeplink`는 어떤 앱이 열릴지 알 수 없으므로 세션 중에는 건너뜁니다. 세션 중에 쓸 딥링크에는 꼭 패키지를 적으세요.
- 앱을 열지 않는 단계(`delay`, `media`, `volume`, `dnd`, `torch`, `timer`, `alarm`, `home`)는 항상 실행합니다. 단, 앞에서 앱을 여는 단계(`launch`, `deeplink`, `shortcut`)가 하나라도 건너뛰어지거나 실패했으면 그 뒤의 `media` 단계는 '앞 단계의 앱이 열리지 않아 재생을 건너뜁니다'라며 건너뜁니다. 그대로 재생하면 마지막으로 재생한 다른 앱(등록하지 않은 앱일 수도 있음)이 소리를 내기 때문입니다.
- 종료 상태(컬러)에서는 모든 단계를 실행합니다.
- 한 단계가 실패하거나 건너뛰어도 루틴은 멈추지 않고 다음 단계로 넘어갑니다.
- 루틴은 겹쳐 실행되지 않습니다. 실행 중에 다른 버튼을 누르면 앞 루틴이 끝난 뒤 실행됩니다.
- 루틴으로 흑백을 끄거나 세션을 종료할 수는 없습니다. 세션을 끄는 길은 '종료' 버튼 하나뿐입니다.

## 팁

- **앱을 여는 단계는 마지막에** 두세요. 볼륨·방해 금지처럼 화면이 없는 단계를 먼저 실행하고, 앱은 마지막에 하나 여는 것이 가장 안정적입니다.
- **미디어 재생은 2~3초 뒤에**. 음악 앱을 연 직후에는 재생 버튼을 받을 준비가 안 되어 있을 수 있습니다. `launch`(또는 `deeplink`) → `delay` 2000~3000 → `media play` 순서로 쓰세요.
- 음악을 틀고 지도를 여는 것처럼 앱 두 개를 쓸 때는 음악 앱을 먼저 열어 재생한 뒤, 마지막에 지도를 엽니다.
- 루틴은 흑백 런처가 **기본 홈 앱**일 때 가장 잘 동작합니다. 기본 홈이 아니면 앱 연속 실행과 바로가기가 막힐 수 있습니다.
- 새 루틴은 세션 중에도 눌러 보세요. 등록 앱이 빠져서 건너뛰는 단계가 없는지 확인할 수 있습니다.

## 예시

### 집 가기 (기본 루틴)

현재 위치에서 집까지 길찾기를 엽니다. 집 좌표(`home_lat`, `home_lng`)를 먼저 저장하세요. 이동수단은 `home_by`로 바꿉니다.

```json
{
  "id": "go_home",
  "label": "집 가기",
  "steps": [
    { "type": "deeplink", "uri": "kakaomap://route?ep={home_lat},{home_lng}&by={home_by}", "packageName": "net.daum.android.map" }
  ]
}
```

### 음악

```json
{
  "id": "music",
  "label": "음악",
  "steps": [
    { "type": "launch", "packageName": "com.google.android.apps.youtube.music" },
    { "type": "delay", "ms": 3000 },
    { "type": "media", "action": "play" }
  ]
}
```

YouTube Music Premium이 없으면 다른 앱으로 넘어갔을 때 재생이 멈출 수 있습니다.

### 독서 (기본 루틴)

교보eBook for 삼성을 열고, 없으면 Play판 교보eBook을 엽니다.

```json
{
  "id": "read",
  "label": "독서",
  "steps": [
    { "type": "launch", "packageName": "com.kyobo.ebook.samsung", "alternatives": ["com.kyobo.ebook.common.b2c"] }
  ]
}
```

### 오디오북 (기본 루틴)

밀리의서재(Play판, 없으면 갤럭시 스토어판)를 열고 마지막에 듣던 오디오북을 이어서 재생합니다. 재생 키는 마지막으로 재생한 앱에 전달되므로, 밀리의서재가 마지막 재생 앱이어야 합니다.

```json
{
  "id": "audiobook",
  "label": "오디오북",
  "steps": [
    { "type": "launch", "packageName": "kr.co.millie.millieshelf", "alternatives": ["kr.co.millie.millieshelf.samsung"] },
    { "type": "delay", "ms": 4000 },
    { "type": "media", "action": "play" }
  ]
}
```

### 퇴근길: 음악 틀고 집 길찾기

```json
{
  "id": "commute_home",
  "label": "퇴근길",
  "steps": [
    { "type": "volume", "percent": 40 },
    { "type": "deeplink", "uri": "https://music.youtube.com/playlist?list=재생목록ID", "packageName": "com.google.android.apps.youtube.music" },
    { "type": "delay", "ms": 3000 },
    { "type": "media", "action": "play" },
    { "type": "deeplink", "uri": "kakaomap://route?ep={home_lat},{home_lng}&by={home_by}", "packageName": "net.daum.android.map" }
  ]
}
```

### 독서 30분

타이머는 삼성 시계 앱을 열 수 있습니다. 방해 금지는 루틴이 끝나도 켜진 채로 남으니, 끌 때는 `{ "type": "dnd", "on": false }` 단계가 있는 루틴을 따로 만드세요.

```json
{
  "id": "reading",
  "label": "독서 30분",
  "steps": [
    { "type": "dnd", "on": true },
    { "type": "timer", "seconds": 1800, "message": "독서 끝" },
    { "type": "launch", "packageName": "kr.co.millie.millieshelf" }
  ]
}
```

### Google Home (기본 루틴)

Google Home 자동화(조명 등)를 바로 실행하는 공개된 방법은 없어서 앱을 여는 데까지만 합니다.

```json
{
  "id": "smart_home",
  "label": "Google Home",
  "steps": [
    { "type": "launch", "packageName": "com.google.android.apps.chromecast.app" }
  ]
}
```
