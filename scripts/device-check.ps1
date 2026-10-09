<#
실기기 확인(docs/PLAN.md §13)용 읽기 전용 점검. 기기 설정은 하나도 바꾸지 않습니다.

사용법:
  powershell -ExecutionPolicy Bypass -File device-check.ps1
#>

$Launcher = 'app.monolauncher'
$KnownApps = [ordered]@{
    'net.daum.android.map'                   = '카카오맵'
    'com.google.android.apps.youtube.music'  = 'YouTube Music'
    'com.google.android.apps.chromecast.app' = 'Google Home'
}

function Section([string]$Title) { Write-Host ''; Write-Host "=== $Title ===" -ForegroundColor Cyan }
function Fail([string]$Msg) { Write-Host ''; Write-Host "[오류] $Msg" -ForegroundColor Red; exit 1 }

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

# Output lines of `adb shell <args>`, without CRs; errors are ignored.
function Invoke-Shell([string[]]$ShellArgs) {
    & $script:Adb shell @ShellArgs 2>$null | ForEach-Object { "$_".TrimEnd("`r") }
}

function Get-Prop([string]$Name) { "$(Invoke-Shell @('getprop', $Name))".Trim() }

$script:Adb = Find-Adb
if (-not $script:Adb) { Fail 'adb를 찾을 수 없습니다. Android SDK Platform-Tools를 설치하세요 (docs/INSTALL.md 참고).' }

& $script:Adb start-server 2>$null | Out-Null
$ready = @(& $script:Adb devices 2>$null | Where-Object { "$_" -match '^\S+\s+device$' })
if ($ready.Count -ne 1) {
    Fail "기기가 정확히 1대 연결되어 있어야 합니다 (지금 $($ready.Count)대).`n    USB 디버깅 허용 창, 케이블, 삼성 자동 차단기(설정 > 보안 및 개인정보 보호 > 자동 차단기)를 확인하세요."
}

Section '기기와 버전'
$oneui = Get-Prop 'ro.build.version.oneui'
Write-Host "모델:    $(Get-Prop 'ro.product.model')"
Write-Host "Android: $(Get-Prop 'ro.build.version.release') (API $(Get-Prop 'ro.build.version.sdk'))"
if ($oneui -match '^\d+$') {
    $v = [int]$oneui
    Write-Host "One UI:  $([math]::Floor($v / 10000)).$([math]::Floor(($v % 10000) / 100)) (ro.build.version.oneui=$oneui)"
} else {
    Write-Host 'One UI:  (ro.build.version.oneui 없음)'
}
Write-Host "빌드:    $(Get-Prop 'ro.build.display.id')"

Section '흑백 관련 secure 설정 (dalton|gray|color)'
$secure = @(Invoke-Shell @('settings', 'list', 'secure') | Where-Object { $_ -match 'dalton|gray|color' })
if ($secure.Count -gt 0) { $secure | ForEach-Object { Write-Host $_ } } else { Write-Host '(일치하는 항목 없음)' }
Write-Host ''
Write-Host '삼성 흑백(회색조) 토글을 켠 상태와 끈 상태에서 이 스크립트를 각각 실행해 위 목록을 비교하면 실제 키를 알 수 있습니다.'

Section '루틴 대상 앱 설치 여부'
$pkgs = @(Invoke-Shell @('pm', 'list', 'packages') | ForEach-Object { $_ -replace '^package:', '' })
$targets = @()
foreach ($pkg in $KnownApps.Keys) {
    if ($pkgs -contains $pkg) {
        Write-Host "  [설치됨] $($KnownApps[$pkg]) ($pkg)"
        $targets += $pkg
    } else {
        Write-Host "  [없음]   $($KnownApps[$pkg]) ($pkg)"
    }
}
$extra = @($pkgs | Where-Object { $_ -match 'millie|kyobo' })
if ($extra.Count -gt 0) {
    Write-Host '  밀리의서재·교보eBook 후보 (millie|kyobo):'
    $extra | ForEach-Object { Write-Host "    $_" }
    $targets += $extra
} else {
    Write-Host '  millie|kyobo 와 일치하는 패키지 없음'
}

Section '앱 바로가기 (루틴 shortcut 단계의 shortcutId)'
if ($targets.Count -eq 0) {
    Write-Host '(확인할 앱 없음)'
} else {
    $current = $null
    $found = 0
    foreach ($line in (Invoke-Shell @('dumpsys', 'shortcut'))) {
        if ($line -match '^\s*Package:\s+(\S+)') {
            $current = if ($targets -contains $Matches[1]) { $Matches[1] } else { $null }
        } elseif ($current -and $line -match 'ShortcutInfo \{') {
            $id = if ($line -match 'id=([^,]*)') { $Matches[1] } else { '' }
            $label = if ($line -match 'shortLabel=([^,]*)') { $Matches[1] } else { '' }
            Write-Host "  $current  id=$id  label=$label"
            $found++
        }
    }
    if ($found -eq 0) { Write-Host '(바로가기 없음. 앱을 한 번 실행한 뒤 다시 확인해 보세요.)' }
    Write-Host ''
    Write-Host '루틴에서 쓰는 법: {"type": "shortcut", "packageName": "<패키지>", "shortcutId": "<id>"}'
}

Section '흑백 런처 상태'
if ($pkgs -contains $Launcher) {
    $dump = (Invoke-Shell @('dumpsys', 'package', $Launcher)) -join "`n"
    if ($dump.Contains('android.permission.WRITE_SECURE_SETTINGS: granted=true')) {
        Write-Host '흑백 권한: 있음'
    } else {
        Write-Host '흑백 권한: 없음 (scripts\install.ps1 을 다시 실행하세요)'
    }
} else {
    Write-Host '흑백 런처: 설치되지 않음'
}
Write-Host "현재 기본 홈: $((Invoke-Shell @('cmd', 'role', 'get-role-holders', '--user', '0', 'android.app.role.HOME')) -join ' ')"

$enabled = "$(Invoke-Shell @('settings', 'get', 'secure', 'accessibility_display_daltonizer_enabled'))".Trim()
$mode = "$(Invoke-Shell @('settings', 'get', 'secure', 'accessibility_display_daltonizer'))".Trim()
function Get-RevertCommand([string]$Key, [string]$Value) {
    if (-not $Value -or $Value -eq 'null') { return "adb shell settings delete secure $Key" }
    return "adb shell settings put secure $Key $Value"
}

Section '직접 해 볼 흑백 쓰기 테스트'
Write-Host @"
이 스크립트는 설정을 바꾸지 않습니다. 아래 명령을 직접 실행하세요.

1) 흑백 켜기 (순서 중요: 모드 0(단색)을 먼저 쓰고 그다음 켭니다)
     adb shell settings put secure accessibility_display_daltonizer 0
     adb shell settings put secure accessibility_display_daltonizer_enabled 1

2) 확인
   - 커버 화면과 메인 화면이 모두 흑백인지
   - 재부팅 후에도 흑백이 유지되는지

3) 되돌리기 (지금 값: enabled=$enabled, mode=$mode)
     $(Get-RevertCommand 'accessibility_display_daltonizer_enabled' $enabled)
     $(Get-RevertCommand 'accessibility_display_daltonizer' $mode)
   또는 폰의 설정 > 접근성 에서 색상 보정(회색조)을 끕니다.

그 밖의 확인 (docs/PLAN.md §13)
   - 자동 차단기를 켠 상태에서 USB 디버깅이 막히는지
   - 런처가 뒤에 있을 때 루틴이 앱을 연달아 여는지: adb logcat | Select-String -Pattern BAL
"@
