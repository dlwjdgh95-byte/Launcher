#!/usr/bin/env bash
# 릴리스 서명용 키스토어를 만들고 GitHub Secrets에 넣을 값을 출력합니다. 처음 한 번만 실행하세요.
# 사용법: make-keystore.sh [키스토어 파일 이름]   (기본: monolauncher-release.jks)
#   KEY_ALIAS 환경 변수로 별칭을 바꿀 수 있습니다 (기본: monolauncher).
set -euo pipefail

OUT=${1:-monolauncher-release.jks}
ALIAS=${KEY_ALIAS:-monolauncher}

die() { printf '\n[오류] %s\n' "$*" >&2; exit 1; }

command -v keytool >/dev/null 2>&1 || die "keytool이 없습니다. JDK 17 이상을 설치하세요."
[ -e "$OUT" ] && die "$OUT 파일이 이미 있습니다. 기존 키를 덮어쓰면 업데이트 설치가 불가능해지므로 다른 이름을 쓰세요."

read -r -s -p "키스토어 비밀번호 (6자 이상): " password; echo
[ ${#password} -ge 6 ] || die "비밀번호는 6자 이상이어야 합니다."
read -r -s -p "비밀번호 확인: " again; echo
[ "$password" = "$again" ] || die "두 비밀번호가 다릅니다."

# PKCS12 keeps a single password for the store and the key, so KEY_PASSWORD = KEYSTORE_PASSWORD.
export MONO_KS_PASS=$password
keytool -genkeypair \
  -keystore "$OUT" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=MonoLauncher" \
  -storepass:env MONO_KS_PASS -keypass:env MONO_KS_PASS
unset MONO_KS_PASS

encoded=$(base64 < "$OUT" | tr -d '\n')

cat <<EOF

키스토어를 만들었습니다: $OUT

GitHub 저장소 > Settings > Secrets and variables > Actions > New repository secret 에서
아래 4개를 추가하세요.

  KEYSTORE_BASE64   = 아래의 긴 문자열 전체
  KEYSTORE_PASSWORD = 방금 입력한 비밀번호
  KEY_ALIAS         = $ALIAS
  KEY_PASSWORD      = 방금 입력한 비밀번호 (KEYSTORE_PASSWORD와 같음)

----- KEYSTORE_BASE64 시작 -----
$encoded
----- KEYSTORE_BASE64 끝 -----

(gh CLI가 있으면: gh secret set KEYSTORE_BASE64 < <(base64 < "$OUT" | tr -d '\\n'))

반드시 할 일
  - $OUT 파일과 비밀번호를 USB나 종이 같은 오프라인 저장소에 백업하세요.
    잃어버리면 같은 앱으로 업데이트할 수 없어, 앱을 지우고 다시 설치한 뒤 adb 권한도 다시 줘야 합니다.
  - 이 파일은 저장소에 커밋하지 마세요 (.gitignore에 *.jks가 들어 있습니다).
  - 위 문자열이 남은 터미널 기록은 지우는 것이 좋습니다.
EOF
