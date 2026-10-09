#!/usr/bin/env bash
# 흑백 런처를 USB로 연결한 폰에 설치하고 흑백 권한(WRITE_SECURE_SETTINGS)을 부여합니다.
# 사용법: install.sh [APK 경로]
#   APK를 생략하면 이 스크립트가 있는 폴더와 현재 폴더에서 가장 최근 monolauncher*.apk를 찾습니다.
#   서명되지 않은 *-unsigned.apk는 설치할 수 없으므로 건너뜁니다.
set -euo pipefail

PKG=app.monolauncher
PERM=android.permission.WRITE_SECURE_SETTINGS
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

step() { printf '\n==> %s\n' "$*"; }
ok() { printf '    [완료] %s\n' "$*"; }
warn() { printf '    [주의] %s\n' "$*"; }
die() { printf '\n[오류] %s\n' "$*" >&2; exit 1; }

auto_blocker_hint() {
  cat >&2 <<'EOF'

    삼성 '자동 차단기'가 켜져 있으면 USB 명령과 앱 설치가 막힙니다.
    폰에서 설정 > 보안 및 개인정보 보호 > 자동 차단기 를 잠시 끄고 다시 실행하세요.
    (설치가 끝나면 다시 켜 주세요.)
EOF
}

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

find_apk() {
  local candidates=()
  shopt -s nullglob
  candidates+=("$SCRIPT_DIR"/monolauncher*.apk ./monolauncher*.apk)
  candidates+=("$SCRIPT_DIR"/../app/build/outputs/apk/release/*.apk "$SCRIPT_DIR"/../app/build/outputs/apk/debug/*.apk)
  shopt -u nullglob
  [ ${#candidates[@]} -gt 0 ] || return 1
  local newest="" f
  for f in "${candidates[@]}"; do
    case $f in
      # Built without a signing config; adb refuses it with INSTALL_PARSE_FAILED_NO_CERTIFICATES.
      *-unsigned.apk) printf '    [주의] 서명되지 않은 APK라 건너뜁니다: %s\n' "$f" >&2; continue ;;
    esac
    if [ -z "$newest" ] || [ "$f" -nt "$newest" ]; then newest=$f; fi
  done
  [ -n "$newest" ] || return 1
  echo "$newest"
}

# --- 1. adb -------------------------------------------------------------------
step "adb 확인"
ADB=$(find_adb) || die "adb를 찾을 수 없습니다. Android SDK Platform-Tools를 설치하세요 (docs/INSTALL.md 참고).
    macOS: brew install --cask android-platform-tools"
ok "$ADB"

# --- 2. APK -------------------------------------------------------------------
step "APK 확인"
if [ $# -ge 1 ]; then
  APK=$1
else
  APK=$(find_apk) || die "설치할 APK를 찾지 못했습니다. 사용법: $0 [APK 경로]"
fi
[ -f "$APK" ] || die "APK 파일이 없습니다: $APK"
ok "$APK"

# --- 3. 기기 ------------------------------------------------------------------
step "연결된 기기 확인"
"$ADB" start-server >/dev/null 2>&1 || true
device_list=$("$ADB" devices | tr -d '\r' | awk 'NR > 1 && NF >= 2')
ready=$(printf '%s\n' "$device_list" | awk '$2 == "device" { print $1 }')
count=$(printf '%s' "$ready" | grep -c . || true)
if [ "$count" -eq 0 ]; then
  case $device_list in
    *unauthorized*) die "폰에서 'USB 디버깅을 허용하시겠습니까?' 창을 허용한 뒤 다시 실행하세요." ;;
  esac
  printf '\n[오류] 연결된 기기가 없습니다. USB 케이블과 개발자 옵션 > USB 디버깅을 확인하세요.\n' >&2
  auto_blocker_hint
  exit 1
fi
[ "$count" -eq 1 ] || die "기기가 ${count}대 연결되어 있습니다. 설치할 폰 하나만 연결하세요."
# Other entries (unauthorized, offline) would make every plain adb command fail with "more than one device".
export ANDROID_SERIAL="$ready"
model=$("$ADB" shell getprop ro.product.model | tr -d '\r')
ok "$ready ($model)"

# --- 4. 설치 ------------------------------------------------------------------
step "앱 설치 (adb install -r -g)"
if ! out=$("$ADB" install -r -g "$APK" 2>&1); then
  printf '%s\n' "$out" >&2
  case $out in
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
      die "이미 설치된 앱과 서명 키가 다릅니다. 같은 키로 서명된 APK를 쓰세요.
    (앱을 지우고 다시 설치할 수는 있지만, 먼저 런처에서 '종료'로 색을 되돌리세요. 설정과 권한은 모두 사라집니다.)" ;;
    *INSTALL_FAILED_VERSION_DOWNGRADE*)
      die "설치된 버전보다 오래된 APK입니다. 최신 APK를 받으세요." ;;
    *INSTALL_PARSE_FAILED_NO_CERTIFICATES*)
      die "서명되지 않은 APK라 설치할 수 없습니다 (예: app-release-unsigned.apk).
    GitHub Releases의 APK나 디버그 APK처럼 서명된 APK를 쓰세요.
    직접 릴리스 빌드를 하려면 keystore.properties가 있어야 합니다 (README.md 참고)." ;;
    *[Bb]locked*|*BLOCKED*|*USB*|*USER_RESTRICTED*|*VERIFICATION_FAILURE*)
      printf '\n[오류] 설치가 막혔습니다.\n' >&2
      auto_blocker_hint
      exit 1 ;;
    *)
      die "설치에 실패했습니다. 위 메시지를 확인하세요." ;;
  esac
fi
ok "설치됨"

# --- 5. 흑백 권한 ---------------------------------------------------------------
step "흑백 권한 부여 (WRITE_SECURE_SETTINGS)"
if ! out=$("$ADB" shell pm grant "$PKG" "$PERM" 2>&1); then
  printf '%s\n' "$out" >&2
  warn "권한 부여 명령이 실패했습니다. 아래 확인 결과를 보세요."
fi
# Capture first: grep -q would cut the pipe short and trip pipefail.
dump=$("$ADB" shell dumpsys package "$PKG" 2>&1 | tr -d '\r' || true)
if [[ $dump == *"$PERM: granted=true"* ]]; then
  ok "granted=true 확인"
else
  printf '\n[오류] 흑백 권한이 부여되지 않았습니다. 직접 실행해 보세요:\n    adb shell pm grant %s %s\n' "$PKG" "$PERM" >&2
  auto_blocker_hint
  exit 1
fi

# --- 6. 기본 홈 ---------------------------------------------------------------
step "기본 홈 앱으로 지정"
"$ADB" shell cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null 2>&1 || true
holders=$("$ADB" shell cmd role get-role-holders --user 0 android.app.role.HOME 2>/dev/null | tr -d '\r' || true)
if [[ $holders == *"$PKG"* ]]; then
  ok "기본 홈 앱으로 지정됨"
else
  warn "자동으로 지정하지 못했습니다. 홈 버튼을 눌러 '흑백 런처'를 고르거나,"
  warn "앱의 안내 또는 폰 설정 > 애플리케이션 > 기본 앱 선택 > 홈 화면 앱 에서 지정하세요."
fi

# --- 7. 다음 단계 -------------------------------------------------------------
cat <<'EOF'

설치가 끝났습니다. 남은 일:
  1. 홈 버튼을 눌러 흑백 런처가 뜨는지, 커버 화면과 메인 화면이 모두 흑백인지 확인하세요.
  2. 앱에서 '종료' 후 설정을 열어 등록 앱과 루틴 변수(집 위치 등)를 확인하세요.
  3. 자동 차단기를 다시 켜세요: 설정 > 보안 및 개인정보 보호 > 자동 차단기
  4. USB 디버깅을 끄세요: 설정 > 개발자 옵션 > USB 디버깅
     (켜 두면 PC로 언제든 흑백을 풀 수 있습니다. 업데이트할 때만 켜세요.)
EOF
