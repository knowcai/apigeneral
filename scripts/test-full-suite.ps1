# Full integration test: auth/roles, API response, circuit breaker, audit, publish
# Usage: .\test-full-suite.ps1 [-SkipSlow]
param([switch]$SkipSlow)

$base = "http://localhost:8088"
$failed = 0
$passed = 0
$script:authHeaders = @{}
$createdUserIds = @()

function Assert-Test($name, $cond, $detail = "") {
    Write-Host "--- $name ---"
    if ($detail) { Write-Host $detail }
    if (-not $cond) {
        Write-Host "[FAIL] $name" -ForegroundColor Red
        $script:failed++
    } else {
        Write-Host "[PASS] $name" -ForegroundColor Green
        $script:passed++
    }
}

function Login-As($username, $password) {
    $body = (@{ username = $username; password = $password } | ConvertTo-Json)
    $resp = Invoke-RestMethod -Method Post -Uri ($base + "/admin/auth/login") -ContentType "application/json" -Body $body
    $script:authHeaders = @{ Authorization = "Bearer " + $resp.data.token }
    return $resp.data.user
}

function Admin($Method, $Uri, $BodyObj = $null) {
    $params = @{ Method = $Method; Uri = $Uri; Headers = $script:authHeaders }
    if ($BodyObj) {
        $params.ContentType = "application/json"
        $params.Body = ($BodyObj | ConvertTo-Json -Depth 10)
    }
    return Invoke-RestMethod @params
}

function Admin-ExpectFail($Method, $Uri, $BodyObj = $null) {
    try {
        Admin $Method $Uri $BodyObj | Out-Null
        return $false
    } catch {
        return $true
    }
}

function Invoke-DataApi($url) {
    $raw = curl.exe -s -w "__HTTP__:%{http_code}" "$url"
    if ($raw -match '__HTTP__:(\d+)\s*$') {
        return @{ Status = [int]$Matches[1]; Body = ($raw -replace '__HTTP__:\d+\s*$', '').Trim() }
    }
    return @{ Status = 0; Body = $raw }
}

function Ensure-TestApi($apiCode, $sql, $respConfig, $theme = "test") {
    $apis = (Admin Get ($base + "/admin/apis")).data
    $api = $apis | Where-Object { $_.apiCode -eq $apiCode } | Select-Object -First 1
    if (-not $api) {
        $api = (Admin Post ($base + "/admin/apis") @{
            apiCode = $apiCode; name = $apiCode; theme = $theme; description = "full test"
        }).data
    }
    $vers = (Admin Get ($base + "/admin/apis/" + $api.id + "/versions")).data
    $ver = $vers | Where-Object { $_.versionNo -eq 1 } | Select-Object -First 1
    if (-not $ver) {
        $ver = (Admin Post ($base + "/admin/apis/" + $api.id + "/versions") @{
            datasourceId = 1; sqlTemplate = $sql; responseMode = "PAGE"; responseConfig = $respConfig
        }).data
    }
    if ($ver.status -ne "PUBLISHED") {
        Admin Post ($base + "/admin/apis/versions/" + $ver.id + "/publish") | Out-Null
    }
    $path = "$base/api/data/v1/$theme/$apiCode"
    return @{ Api = $api; Version = $ver; Path = $path; ApiId = $api.id; VerId = $ver.id }
}

function Get-AuditActions($action) {
    $page = (Admin Get ($base + "/admin/audit-logs?page=0&size=50")).data
    return @($page.content | Where-Object { $_.action -eq $action })
}

function Set-Policy($patch) {
    $p = (Admin Get ($base + "/admin/gateway-policy")).data
    foreach ($k in $patch.Keys) { $p.$k = $patch[$k] }
    Admin Put ($base + "/admin/gateway-policy") $p | Out-Null
}

$editorUser = "test_editor_" + (Get-Random -Maximum 99999)
$viewerUser = "test_viewer_" + (Get-Random -Maximum 99999)
$ownApiCode = "editor-own-" + (Get-Random -Maximum 99999)
$adminApiCode = "admin-only-" + (Get-Random -Maximum 99999)
$editorPass = "edit123456"
$viewerPass = "view123456"

try {
    Write-Host "=== D. Auth & Roles ===" -ForegroundColor Cyan

    $bad = $false
    try { $null = Login-As "admin" "wrongpass" } catch { $bad = $true }
    Assert-Test "D1 wrong password rejected" $bad

    $null = Login-As "admin" "admin123"
    $me = (Admin Get ($base + "/admin/auth/me")).data
    Assert-Test "D2 admin login + me" ($me.role -eq "SUPER_ADMIN") ("role=$($me.role)")

    $script:authHeaders = @{}
    $noAuth = $false
    try { (Invoke-RestMethod ($base + "/admin/apis")).data } catch { $noAuth = $true }
    Assert-Test "D3 unauthenticated blocked" $noAuth

    Login-As "admin" "admin123"
    $editor = (Admin Post ($base + "/admin/users") @{
        username = $editorUser; displayName = "Test Editor"; role = "API_EDITOR"
        password = $editorPass; enabled = $true
    }).data
    $createdUserIds += $editor.id
    $viewer = (Admin Post ($base + "/admin/users") @{
        username = $viewerUser; displayName = "Test Viewer"; role = "API_VIEWER"
        password = $viewerPass; enabled = $true
    }).data
    $createdUserIds += $viewer.id
    Assert-Test "D4 create editor and viewer" ($editor.id -and $viewer.id)

    Login-As $viewerUser $viewerPass
    Assert-Test "D5 viewer cannot create API" (Admin-ExpectFail Post ($base + "/admin/apis") @{
        apiCode = "viewer-api"; name = "x"; theme = "test"
    })
    Assert-Test "D6 viewer cannot update policy" (Admin-ExpectFail Put ($base + "/admin/gateway-policy") @{
        globalQpsEnabled = $false
    })

    Login-As $editorUser $editorPass
    $ownApi = (Admin Post ($base + "/admin/apis") @{
        apiCode = $ownApiCode; name = "editor own"; theme = "test"
    }).data
    Assert-Test "D7 editor creates own API" ($ownApi.createdBy -eq $editorUser) ("createdBy=$($ownApi.createdBy)")

    Login-As "admin" "admin123"
    $adminApi = (Admin Post ($base + "/admin/apis") @{
        apiCode = $adminApiCode; name = "admin api"; theme = "test"
    }).data

    Login-As $editorUser $editorPass
    Assert-Test "D8 editor cannot edit admin API" (Admin-ExpectFail Put ($base + "/admin/apis/" + $adminApi.id) @{
        apiCode = $adminApiCode; name = "hacked"; theme = "test"
    })

    $ownOk = (Admin Put ($base + "/admin/apis/" + $ownApi.id) @{
        apiCode = $ownApiCode; name = "editor updated"; theme = "test"
    }).data
    Assert-Test "D9 editor edits own API" ($ownOk.name -eq "editor updated")

    Login-As $editorUser $editorPass
    Assert-Test "D10 editor cannot manage users" (Admin-ExpectFail Get ($base + "/admin/users"))

    Login-As "admin" "admin123"
    Assert-Test "D11 admin can list users" (@((Admin Get ($base + "/admin/users")).data).Count -ge 2)

    Write-Host ""
    Write-Host "=== E. API Response Config ===" -ForegroundColor Cyan
    Login-As "admin" "admin123"
    $respApi = Ensure-TestApi ("full-resp-" + (Get-Random -Maximum 99999)) "SELECT pg_sleep(3) AS ok WHERE :id = 1" @{
        maxPageSize = 10; maxOffset = 50; timeoutSec = 1
    }
    $r1 = Invoke-DataApi ($respApi.Path + "?id=1&page=1&pageSize=11")
    Assert-Test "E1 maxPageSize reject" ($r1.Status -eq 400) "HTTP $($r1.Status) $($r1.Body)"
    $r2 = Invoke-DataApi ($respApi.Path + "?id=1&page=6&pageSize=10")
    Assert-Test "E2 maxOffset reject" ($r2.Status -eq 400) "HTTP $($r2.Status)"
    $r3 = Invoke-DataApi ($respApi.Path + "?id=1&page=1&pageSize=10")
    Assert-Test "E3 timeout reject" ($r3.Status -eq 400) "HTTP $($r3.Status)"

    $fastCode = "full-resp-ok-" + (Get-Random -Maximum 99999)
    $fast = Ensure-TestApi $fastCode "SELECT 1 AS ok WHERE :id = 1" @{
        maxPageSize = 100; timeoutSec = 10
    }
    $ver2 = (Admin Post ($base + "/admin/apis/" + $fast.ApiId + "/versions") @{
        datasourceId = 1; sqlTemplate = "SELECT 1 AS ok WHERE :id = 1"
        responseMode = "PAGE"; responseConfig = @{ maxPageSize = 100; timeoutSec = 10 }
    }).data
    Admin Post ($base + "/admin/apis/versions/" + $ver2.id + "/publish") | Out-Null
    $r4 = Invoke-DataApi ($base + "/api/data/v2/test/" + $fastCode + "?id=1&page=1&pageSize=10")
    Assert-Test "E4 published v2 success" ($r4.Status -eq 200 -and $r4.Body -match "rows") "HTTP $($r4.Status)"

    Write-Host ""
    Write-Host "=== F. API Circuit Breaker ===" -ForegroundColor Cyan
    Set-Policy @{
        circuitEnabled = $true; circuitMinCalls = 5; circuitFailureRate = 50
        circuitWaitSec = 5; globalQpsEnabled = $false; ipQpsEnabled = $false; apiQpsEnabled = $false
        retryEnabled = $false
    }
    $failApi = Ensure-TestApi ("full-cb-" + (Get-Random -Maximum 99999)) "SELECT * FROM default.not_exists_table WHERE id = :id" @{
        maxPageSize = 100; timeoutSec = 10
    }
    1..6 | ForEach-Object { Invoke-DataApi ($failApi.Path + "?id=1&page=1&pageSize=10") | Out-Null }
    $cb = Invoke-DataApi ($failApi.Path + "?id=1&page=1&pageSize=10")
    Assert-Test "F1 circuit 503" ($cb.Status -eq 503) "HTTP $($cb.Status)"
    $okPath = "$base/api/data/v1/test/" + $fastCode + "?id=1&page=1&pageSize=10"
    $iso = Invoke-DataApi $okPath
    Assert-Test "F2 circuit isolated per apiCode" ($iso.Status -eq 200) "HTTP $($iso.Status)"

    if (-not $SkipSlow) {
        Write-Host ""
        Write-Host "=== F3 rolling 60s window (~65s) ===" -ForegroundColor Cyan
        Set-Policy @{ circuitEnabled = $true; circuitMinCalls = 5; circuitWaitSec = 5 }
        $winApi = Ensure-TestApi ("full-cb-win-" + (Get-Random -Maximum 99999)) "SELECT * FROM default.not_exists_table WHERE id = :id" @{
            maxPageSize = 100; timeoutSec = 10
        }
        1..3 | ForEach-Object { Invoke-DataApi ($winApi.Path + "?id=1&page=1&pageSize=10") | Out-Null }
        Write-Host "waiting 62s..."
        Start-Sleep -Seconds 62
        1..3 | ForEach-Object { Invoke-DataApi ($winApi.Path + "?id=1&page=1&pageSize=10") | Out-Null }
        $after = Invoke-DataApi ($winApi.Path + "?id=1&page=1&pageSize=10")
        Assert-Test "F3 window expired no false open" ($after.Status -eq 400) "HTTP $($after.Status)"
    } else {
        Write-Host "=== F3 rolling window skipped (-SkipSlow) ===" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "=== G. Operation Audit ===" -ForegroundColor Cyan
    Login-As "admin" "admin123"
    $logins = Get-AuditActions "LOGIN"
    $creates = Get-AuditActions "CREATE"
    $updates = Get-AuditActions "UPDATE"
    Assert-Test "G1 audit has LOGIN" ($logins.Count -ge 1) ("LOGIN=$($logins.Count)")
    Assert-Test "G2 audit has CREATE" ($creates.Count -ge 1) ("CREATE=$($creates.Count)")
    Assert-Test "G3 audit has UPDATE" ($updates.Count -ge 1) ("UPDATE=$($updates.Count)")
    $userLogs = @((Admin Get ($base + "/admin/audit-logs?page=0&size=50")).data.content | Where-Object {
        $_.resourceType -eq "USER" -and $_.username -eq "admin"
    })
    Assert-Test "G4 user create audited" ($userLogs.Count -ge 1) ("USER logs=$($userLogs.Count)")

    Write-Host ""
    Write-Host "=== H. Publish (in-flight guard) ===" -ForegroundColor Cyan
    Set-Policy @{ circuitEnabled = $false }
    $pubCode = "full-pub-" + (Get-Random -Maximum 99999)
    $pubApi = Ensure-TestApi $pubCode "SELECT pg_sleep(15) AS ok WHERE :id = 1" @{
        maxPageSize = 100; timeoutSec = 30
    }
    $v2 = (Admin Post ($base + "/admin/apis/" + $pubApi.ApiId + "/versions") @{
        datasourceId = 1; sqlTemplate = "SELECT 2 AS ok WHERE :id = 1"
        responseMode = "PAGE"; responseConfig = @{ maxPageSize = 100; timeoutSec = 10 }
    }).data
    $slowUrl = $pubApi.Path + "?id=1&page=1&pageSize=10"
    $job = Start-Job { param($u) curl.exe -s -o NUL "$u" } -ArgumentList $slowUrl
    Start-Sleep -Seconds 1
    $pubFail = $false
    $pubErr = ""
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 500
        if ($job.State -ne "Running") { break }
        try {
            Admin Post ($base + "/admin/apis/versions/" + $v2.id + "/publish") | Out-Null
        } catch {
            $pubFail = $true
            $pubErr = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
            break
        }
    }
    Wait-Job $job -Timeout 25 | Out-Null
    Remove-Job $job -Force -ErrorAction SilentlyContinue
    Assert-Test "H1 publish blocked during in-flight" $pubFail $pubErr

    Start-Sleep -Seconds 3
    $pubOk = $false
    try {
        Admin Post ($base + "/admin/apis/versions/" + $v2.id + "/publish") | Out-Null
        $pubOk = $true
    } catch {
        $pubErr = $_.Exception.Message
    }
    Assert-Test "H2 publish succeeds when idle" $pubOk $pubErr

    Write-Host ""
    Write-Host "=== Policy tests: run scripts/test-gateway-policy.ps1 -SkipSlow separately ===" -ForegroundColor Yellow

} finally {
    Login-As "admin" "admin123"
    foreach ($uid in $createdUserIds) {
        try {
            Admin Delete ($base + "/admin/users/" + $uid) | Out-Null
        } catch { }
    }
    Write-Host ""
    Write-Host "=== cleanup test users done ===" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=== SUMMARY: $passed passed, $failed failed ===" -ForegroundColor Cyan
if ($failed -gt 0) { exit 1 }
Write-Host "ALL PASSED" -ForegroundColor Green
