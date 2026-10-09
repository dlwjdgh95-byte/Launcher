<#
흑백 런처를 USB로 연결한 폰에 설치하고 흑백 권한(WRITE_SECURE_SETTINGS)을 부여합니다.

사용법:
  powershell -ExecutionPolicy Bypass -File install.ps1 [APK 경로]

APK를 생략하면 이 스크립트가 있는 폴더와 현재 폴더에서 가장 최근 monolauncher*.apk를 찾습니다.
#>
param([string]$Apk)

$Pkg = 'app.monolauncher'
$Perm = 'android.permission.WRITE_SECURE_SETTINGS'

function Step([string]$Msg) { Write-Host ''; Write-Host "==> $Msg" -ForegroundColor Cyan }
function Ok([string]$Msg) { Write-Host "    [완료] $Msg" }
function Warn([string]$Msg) { Write-Host "    [주의] $Msg" -ForegroundColor Yellow }
function Fail([string]$Msg) { Write-Host ''; Write-Host "[오류] $Msg" -ForegroundColor Red; exit 1 }

function Show-AutoBlockerHint {
    Write-Host @'

    삼성 '자동 차단기'가 켜져 있으면 USB 명령과 앱 설치가 막힙니다.
    폰에서 설정 > 보안 및 개인정보 보호 > 자동 차단기 를 잠시 끄고 다시 실행하세요.
    (설치가 끝나면 다시 켜 주세요.)
'@ -ForegroundColor Yellow
}

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @((Join-Path $PSScriptRoot 'platform-tools\adb.exe'), 'C:\platform-tools\adb.exe')
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe') }
    foreach ($root in @($env:ANDROID_HOME, $env:USERPROFILE)) {
        if ($root) { $candidates += (Join-Path $root 'platform-tools\adb.exe') }
    }
    foreach ($c in $candidates) { if (Test-Path -LiteralPath $c) { return $c } }
    return $null
}

function Find-Apk {
    $found = @()
    foreach ($dir in (@($PSScriptRoot, (Get-Location).Path) | Select-Object -Unique)) {
        $found += @(Get-ChildItem -LiteralPath $dir -Filter 'monolauncher*.apk' -File -ErrorAction SilentlyContinue)
    }
    $outputs = Join-Path $PSScriptRoot '..\app\build\outputs\apk'
    if (Test-Path -LiteralPath $outputs) {
        $found += @(Get-ChildItem -LiteralPath $outputs -Filter '*.apk' -File -Recurse -ErrorAction SilentlyContinue)
    }
    $newest = $found | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($newest) { return $newest.FullName }
    return $null
}

# Runs adb and returns its exit code with stdout and stderr merged as text.
function Invoke-Adb([string[]]$AdbArgs) {
    $lines = & $script:Adb @AdbArgs 2>&1 | ForEach-Object { "$_" }
    [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($lines -join "`n") }
}

# --- 1. adb -------------------------------------------------------------------
Step 'adb 확인'
$script:Adb = Find-Adb
if (-not $script:Adb) {
    Fail "adb를 찾을 수 없습니다. Android SDK Platform-Tools를 설치하세요 (docs/INSTALL.md 참고).`n    압축을 C:\platform-tools 에 풀거나 이 스크립트 옆 platform-tools 폴더에 두면 자동으로 찾습니다."
}
Ok $script:Adb

# --- 2. APK -------------------------------------------------------------------
Step 'APK 확인'
if (-not $Apk) {
    $Apk = Find-Apk
    if (-not $Apk) { Fail '설치할 APK를 찾지 못했습니다. 사용법: install.ps1 [APK 경로]' }
}
if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { Fail "APK 파일이 없습니다: $Apk" }
$Apk = (Resolve-Path -LiteralPath $Apk).Path
Ok $Apk

# --- 3. 기기 ------------------------------------------------------------------
Step '연결된 기기 확인'
$null = Invoke-Adb @('start-server')
$deviceLines = (Invoke-Adb @('devices')).Text -split "`r?`n"
$ready = @($deviceLines | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($ready.Count -eq 0) {
    if ($deviceLines -match '\sunauthorized$') {
        Fail "폰에서 'USB 디버깅을 허용하시겠습니까?' 창을 허용한 뒤 다시 실행하세요."
    }
    Write-Host ''
    Write-Host '[오류] 연결된 기기가 없습니다. USB 케이블과 개발자 옵션 > USB 디버깅을 확인하세요.' -ForegroundColor Red
    Show-AutoBlockerHint
    exit 1
}
if ($ready.Count -gt 1) { Fail "기기가 $($ready.Count)대 연결되어 있습니다. 설치할 폰 하나만 연결하세요." }
$model = (Invoke-Adb @('shell', 'getprop', 'ro.product.model')).Text.Trim()
Ok "$($ready[0]) ($model)"

# --- 4. 설치 ------------------------------------------------------------------
Step '앱 설치 (adb install -r -g)'
$r = Invoke-Adb @('install', '-r', '-g', $Apk)
if ($r.Code -ne 0) {
    Write-Host $r.Text
    $t = $r.Text
    if ($t -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        Fail "이미 설치된 앱과 서명 키가 다릅니다. 같은 키로 서명된 APK를 쓰세요.`n    (앱을 지우고 다시 설치할 수는 있지만, 먼저 런처에서 '종료'로 색을 되돌리세요. 설정과 권한은 모두 사라집니다.)"
    }
    if ($t -match 'INSTALL_FAILED_VERSION_DOWNGRADE') { Fail '설치된 버전보다 오래된 APK입니다. 최신 APK를 받으세요.' }
    if ($t -match 'blocked|USB|USER_RESTRICTED|VERIFICATION_FAILURE') {
        Write-Host ''
        Write-Host '[오류] 설치가 막혔습니다.' -ForegroundColor Red
        Show-AutoBlockerHint
        exit 1
    }
    Fail '설치에 실패했습니다. 위 메시지를 확인하세요.'
}
Ok '설치됨'

# --- 5. 흑백 권한 ---------------------------------------------------------------
Step '흑백 권한 부여 (WRITE_SECURE_SETTINGS)'
$r = Invoke-Adb @('shell', 'pm', 'grant', $Pkg, $Perm)
if ($r.Code -ne 0) {
    Write-Host $r.Text
    Warn '권한 부여 명령이 실패했습니다. 아래 확인 결과를 보세요.'
}
$dump = (Invoke-Adb @('shell', 'dumpsys', 'package', $Pkg)).Text
if ($dump.Contains("${Perm}: granted=true")) {
    Ok 'granted=true 확인'
} else {
    Write-Host ''
    Write-Host "[오류] 흑백 권한이 부여되지 않았습니다. 직접 실행해 보세요:`n    adb shell pm grant $Pkg $Perm" -ForegroundColor Red
    Show-AutoBlockerHint
    exit 1
}

# --- 6. 기본 홈 ---------------------------------------------------------------
Step '기본 홈 앱으로 지정'
$null = Invoke-Adb @('shell', 'cmd', 'role', 'add-role-holder', '--user', '0', 'android.app.role.HOME', $Pkg)
$holders = (Invoke-Adb @('shell', 'cmd', 'role', 'get-role-holders', '--user', '0', 'android.app.role.HOME')).Text
if ($holders.Contains($Pkg)) {
    Ok '기본 홈 앱으로 지정됨'
} else {
    Warn "자동으로 지정하지 못했습니다. 홈 버튼을 눌러 '흑백 런처'를 고르거나,"
    Warn '앱의 안내 또는 폰 설정 > 애플리케이션 > 기본 앱 선택 > 홈 화면 앱 에서 지정하세요.'
}

# --- 7. 다음 단계 -------------------------------------------------------------
Write-Host @'

설치가 끝났습니다. 남은 일:
  1. 홈 버튼을 눌러 흑백 런처가 뜨는지, 커버 화면과 메인 화면이 모두 흑백인지 확인하세요.
  2. 앱에서 '종료' 후 설정을 열어 등록 앱과 루틴 변수(집 위치 등)를 확인하세요.
  3. 자동 차단기를 다시 켜세요: 설정 > 보안 및 개인정보 보호 > 자동 차단기
  4. USB 디버깅을 끄세요: 설정 > 개발자 옵션 > USB 디버깅
     (켜 두면 PC로 언제든 흑백을 풀 수 있습니다. 업데이트할 때만 켜세요.)
'@
