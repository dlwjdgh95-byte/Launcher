#!/usr/bin/env bash
# 실기기 확인(docs/PLAN.md §13)용 읽기 전용 점검. 기기 설정은 하나도 바꾸지 않습니다.
# 사용법: device-check.sh
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
LAUNCHER=app.monolauncher
KNOWN_APPS="net.daum.android.map:카카오맵
com.google.android.apps.youtube.music:YouTube Music
com.google.android.apps.chromecast.app:Google Home"

section() { printf '\n=== %s ===\n' "$*"; }
die() { printf '\n[오류] %s\n' "$*" >&2; exit 1; }

find_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  local c
  for c in "$SCRIPT_DIR/platform-tools/adb" \
           "${ANDROID_HOME:-/nonexistent}/platform-tools/adb" \
           "$HOME/Library/Android/sdk/platform-tools/adb" \
           "$HOME/Android/Sdk/platform-tools/adb" \
           "$HOME/platform-tools/adb"; do
    if [ -x "$c" ]; then
      echo "$c"
      return
    fi
  done
  return 1
}

ADB=$(find_adb) || die "adb를 찾을 수 없습니다. Android SDK Platform-Tools를 설치하세요 (docs/INSTALL.md 참고)."

# adb shell without the CRs some devices add; never fails the script.
adb_sh() { "$ADB" shell "$@" 2>/dev/null | tr -d '\r' || true; }

"$ADB" start-server >/dev/null 2>&1 || true
ready=$("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
count=$(printf '%s' "$ready" | grep -c . || true)
if [ "$count" -ne 1 ]; then
  die "기기가 정확히 1대 연결되어 있어야 합니다 (지금 ${count}대).
    USB 디버깅 허용 창, 케이블, 삼성 자동 차단기(설정 > 보안 및 개인정보 보호 > 자동 차단기)를 확인하세요."
fi

section "기기와 버전"
oneui=$(adb_sh getprop ro.build.version.oneui)
echo "모델:    $(adb_sh getprop ro.product.model)"
echo "Android: $(adb_sh getprop ro.build.version.release) (API $(adb_sh getprop ro.build.version.sdk))"
if [[ $oneui =~ ^[0-9]+$ ]]; then
  echo "One UI:  $((oneui / 10000)).$(((oneui % 10000) / 100)) (ro.build.version.oneui=$oneui)"
else
  echo "One UI:  (ro.build.version.oneui 없음)"
fi
echo "빌드:    $(adb_sh getprop ro.build.display.id)"

section "흑백 관련 secure 설정 (dalton|gray|color)"
adb_sh settings list secure | grep -iE 'dalton|gray|color' || echo "(일치하는 항목 없음)"
echo
echo "삼성 흑백(회색조) 토글을 켠 상태와 끈 상태에서 이 스크립트를 각각 실행해 위 목록을 비교하면 실제 키를 알 수 있습니다."

section "루틴 대상 앱 설치 여부"
pkgs=$(adb_sh pm list packages | sed 's/^package://')
targets=""
while IFS=: read -r pkg label; do
  if grep -qxF "$pkg" <<<"$pkgs"; then
    printf '  [설치됨] %s (%s)\n' "$label" "$pkg"
    targets="$targets $pkg"
  else
    printf '  [없음]   %s (%s)\n' "$label" "$pkg"
  fi
done <<<"$KNOWN_APPS"
extra=$(grep -iE 'millie|kyobo' <<<"$pkgs" || true)
if [ -n "$extra" ]; then
  echo "  밀리의서재·교보eBook 후보 (millie|kyobo):"
  while read -r pkg; do
    echo "    $pkg"
    targets="$targets $pkg"
  done <<<"$extra"
else
  echo "  millie|kyobo 와 일치하는 패키지 없음"
fi

section "앱 바로가기 (루틴 shortcut 단계의 shortcutId)"
if [ -z "$targets" ]; then
  echo "(확인할 앱 없음)"
else
  shortcuts=$(adb_sh dumpsys shortcut | awk -v targets="$targets" '
    BEGIN { n = split(targets, t, " "); for (i = 1; i <= n; i++) want[t[i]] = 1 }
    /^[[:space:]]*Package: / { cur = ($2 in want) ? $2 : ""; next }
    cur != "" && /ShortcutInfo \{/ {
      id = ""; label = ""
      if (match($0, /id=[^,]*/)) id = substr($0, RSTART + 3, RLENGTH - 3)
      if (match($0, /shortLabel=[^,]*/)) label = substr($0, RSTART + 11, RLENGTH - 11)
      printf "  %s  id=%s  label=%s\n", cur, id, label
    }')
  if [ -n "$shortcuts" ]; then
    echo "$shortcuts"
  else
    echo "(바로가기 없음. 앱을 한 번 실행한 뒤 다시 확인해 보세요.)"
  fi
  echo
  echo '루틴에서 쓰는 법: {"type": "shortcut", "packageName": "<패키지>", "shortcutId": "<id>"}'
fi

section "흑백 런처 상태"
if grep -qxF "$LAUNCHER" <<<"$pkgs"; then
  dump=$(adb_sh dumpsys package "$LAUNCHER")
  if [[ $dump == *"android.permission.WRITE_SECURE_SETTINGS: granted=true"* ]]; then
    echo "흑백 권한: 있음"
  else
    echo "흑백 권한: 없음 (scripts/install.sh 를 다시 실행하세요)"
  fi
else
  echo "흑백 런처: 설치되지 않음"
fi
echo "현재 기본 홈: $(adb_sh cmd role get-role-holders --user 0 android.app.role.HOME)"

enabled=$(adb_sh settings get secure accessibility_display_daltonizer_enabled)
mode=$(adb_sh settings get secure accessibility_display_daltonizer)
revert_cmd() { # key current-value
  if [ -z "$2" ] || [ "$2" = null ]; then
    echo "adb shell settings delete secure $1"
  else
    echo "adb shell settings put secure $1 $2"
  fi
}

section "직접 해 볼 흑백 쓰기 테스트"
cat <<EOF
이 스크립트는 설정을 바꾸지 않습니다. 아래 명령을 직접 실행하세요.

1) 흑백 켜기 (순서 중요: 모드 0(단색)을 먼저 쓰고 그다음 켭니다)
     adb shell settings put secure accessibility_display_daltonizer 0
     adb shell settings put secure accessibility_display_daltonizer_enabled 1

2) 확인
   - 커버 화면과 메인 화면이 모두 흑백인지
   - 재부팅 후에도 흑백이 유지되는지

3) 되돌리기 (지금 값: enabled=$enabled, mode=$mode)
     $(revert_cmd accessibility_display_daltonizer_enabled "$enabled")
     $(revert_cmd accessibility_display_daltonizer "$mode")
   또는 폰의 설정 > 접근성 에서 색상 보정(회색조)을 끕니다.

그 밖의 확인 (docs/PLAN.md §13)
   - 자동 차단기를 켠 상태에서 USB 디버깅이 막히는지
   - 런처가 뒤에 있을 때 루틴이 앱을 연달아 여는지: adb logcat | grep -i BAL
EOF
