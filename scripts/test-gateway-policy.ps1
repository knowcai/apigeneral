# Comprehensive API gateway policy tests: QPS / circuit breaker / retry
# Usage: .\test-gateway-policy.ps1 [-SkipSlow]
param([switch]$SkipSlow)

$base = "http://localhost:8088"
$failed = 0
$passed = 0
$savedPolicy = $null
$script:authHeaders = @{}

function Invoke-Api($url, $headers = @{}) {
    $args = @("-s", "-w", "__HTTP__:%{http_code}")
    foreach ($k in $headers.Keys) {
        $args += "-H"
        $args += ($k + ": " + $headers[$k])
    }
    $args += $url
    $raw = & curl.exe @args
    if ($raw -match '__HTTP__:(\d+)\s*$') {
        $status = [int]$Matches[1]
        $body = ($raw -replace '__HTTP__:\d+\s*$', '').Trim()
        return @{ Status = $status; Body = $body }
    }
    return @{ Status = 0; Body = $raw }
}

function Invoke-Burst($url, $count, $xff = $null) {
    $jobs = 1..$count | ForEach-Object {
        Start-Job -ScriptBlock {
            param($u, $xff)
            $args = @("-s", "-o", "NUL", "-w", "%{http_code}")
            if ($xff) {
                $args += "-H"
                $args += ("X-Forwarded-For: " + $xff)
            }
            $args += $u
            return (& curl.exe @args 2>$null | Out-String).Trim()
        } -ArgumentList $url, $xff
    }
    return @($jobs | ForEach-Object { Receive-Job $_ -Wait -AutoRemoveJob })
}

function Count-Logs($logs, $status) {
    $n = 0
    foreach ($log in @($logs)) {
        if ($null -ne $log -and $log.status -eq $status) { $n++ }
    }
    return $n
}

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

function Get-Policy {
    return (Invoke-RestMethod ($base + "/admin/gateway-policy") -Headers $script:authHeaders).data
}

function Set-Policy($policy) {
    $json = $policy | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Method Put -Uri ($base + "/admin/gateway-policy") -Headers $script:authHeaders -ContentType "application/json" -Body $json | Out-Null
}

function Admin-RestMethod {
    param([string]$Method = "Get", [string]$Uri, [string]$Body = $null)
    $params = @{ Method = $Method; Uri = $Uri; Headers = $script:authHeaders }
    if ($Body) { $params.ContentType = "application/json"; $params.Body = $Body }
    return Invoke-RestMethod @params
}

function Login-Admin {
    $login = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
    $resp = Invoke-RestMethod -Method Post -Uri ($base + "/admin/auth/login") -ContentType "application/json" -Body $login
    $script:authHeaders = @{ Authorization = "Bearer " + $resp.data.token }
}

function New-BasePolicy {
    $p = Get-Policy
    $p.globalQpsEnabled = $false
    $p.ipQpsEnabled = $false
    $p.apiQpsEnabled = $false
    $p.circuitEnabled = $false
    $p.retryEnabled = $false
    $p.circuitFailureRate = 50
    $p.circuitMinCalls = 5
    $p.circuitWaitSec = 5
    $p.circuitFallback = '{"code":503,"message":"circuit open","data":null}'
    return $p
}

function Ensure-Api($apiCode, $theme, $verPayload, $versionNo = 1) {
    $apis = (Admin-RestMethod -Uri ($base + "/admin/apis")).data
    $api = $apis | Where-Object { $_.apiCode -eq $apiCode } | Select-Object -First 1
    if (-not $api) {
        $body = (@{
            apiCode = $apiCode; name = $apiCode; theme = $theme; description = "policy test"; updatedBy = "admin"
        } | ConvertTo-Json)
        $api = (Admin-RestMethod -Method Post -Uri ($base + "/admin/apis") -Body $body).data
    }
    $versions = (Admin-RestMethod -Uri ($base + "/admin/apis/" + $api.id + "/versions")).data
    $ver = $versions | Where-Object { $_.versionNo -eq $versionNo } | Select-Object -First 1
    if (-not $ver) {
        $body = $verPayload | ConvertTo-Json -Depth 10
        $ver = (Admin-RestMethod -Method Post -Uri ($base + "/admin/apis/" + $api.id + "/versions") -Body $body).data
        Admin-RestMethod -Method Post -Uri ($base + "/admin/apis/versions/" + $ver.id + "/publish?operator=admin") | Out-Null
    }
    $path = $base + "/api/data/v" + $versionNo + "/" + $theme + "/" + $apiCode + "?id=1&page=1&pageSize=10"
    return @{ Api = $api; Version = $ver; Path = $path; ApiId = $api.id }
}

function Publish-Version($apiId, $versionNo) {
    $versions = (Admin-RestMethod -Uri ($base + "/admin/apis/" + $apiId + "/versions")).data
    $ver = $versions | Where-Object { $_.versionNo -eq $versionNo } | Select-Object -First 1
    if ($ver) {
        Admin-RestMethod -Method Post -Uri ($base + "/admin/apis/versions/" + $ver.id + "/publish?operator=admin") | Out-Null
    }
    return $ver
}

function Ensure-TwoVersionApi($apiCode, $theme, $goodFile, $badFile) {
    $good = Get-Content $goodFile -Raw | ConvertFrom-Json
    $bad = Get-Content $badFile -Raw | ConvertFrom-Json
    $v1 = Ensure-Api $apiCode $theme $good 1
    $versions = (Admin-RestMethod -Uri ($base + "/admin/apis/" + $v1.ApiId + "/versions")).data
    $v2 = $versions | Where-Object { $_.versionNo -eq 2 } | Select-Object -First 1
    if (-not $v2) {
        $body = $bad | ConvertTo-Json -Depth 10
        $v2 = (Admin-RestMethod -Method Post -Uri ($base + "/admin/apis/" + $v1.ApiId + "/versions") -Body $body).data
    }
    $pathV1 = $base + "/api/data/v1/" + $theme + "/" + $apiCode + "?id=1&page=1&pageSize=10"
    $pathV2 = $base + "/api/data/v2/" + $theme + "/" + $apiCode + "?id=1&page=1&pageSize=10"
    return @{ ApiId = $v1.ApiId; PathGood = $pathV1; PathBad = $pathV2 }
}

function Get-NewLogs($apiCode, $sinceId) {
    $logUrl = $base + "/admin/logs?page=0&size=20&apiCode=" + $apiCode
    $content = (Admin-RestMethod -Uri $logUrl).data.content
    if (-not $content) { return @() }
    return @($content | Where-Object { $_.id -gt $sinceId })
}

function Get-MaxLogId($apiCode) {
    $logUrl = $base + "/admin/logs?page=0&size=5&apiCode=" + $apiCode
    $max = ((Admin-RestMethod -Uri $logUrl).data.content | Measure-Object -Property id -Maximum).Maximum
    if (-not $max) { return 0 }
    return $max
}

function Open-Circuit($path, $failCount = 6) {
    1..$failCount | ForEach-Object { Invoke-Api $path | Out-Null }
}

$goodPayload = @{
    datasourceId = 1
    sqlTemplate = "SELECT 1 AS ok WHERE :id = 1"
    responseMode = "PAGE"
    responseConfig = @{ maxPageSize = 100; timeoutSec = 10 }
    updatedBy = "admin"
}

$failPayload = Get-Content "$PSScriptRoot/api-fail-version.json" -Raw | ConvertFrom-Json
$qpsOverridePayload = @{
    datasourceId = 1
    sqlTemplate = "SELECT 1 AS ok WHERE :id = 1"
    responseMode = "PAGE"
    responseConfig = @{ maxPageSize = 100; timeoutSec = 10; apiQps = 1 }
    updatedBy = "admin"
}
$ipWhitelistPayload = @{
    datasourceId = 1
    sqlTemplate = "SELECT 1 AS ok WHERE :id = 1"
    responseMode = "PAGE"
    responseConfig = @{ maxPageSize = 100; timeoutSec = 10; ipWhitelist = @("10.0.0.1") }
    updatedBy = "admin"
}
$timeoutPayload = @{
    datasourceId = 1
    sqlTemplate = "SELECT sleep(3) AS ok WHERE :id = 1"
    responseMode = "PAGE"
    responseConfig = @{ maxPageSize = 100; timeoutSec = 1 }
    updatedBy = "admin"
}

try {
    Login-Admin
    Write-Host "=== backup policy ===" -ForegroundColor Cyan
    $savedPolicy = Get-Policy

    $okApi = Ensure-Api "qps-test-ok" "test" $goodPayload
    $failApi = Ensure-Api "circuit-test" "test" $failPayload
    $windowApi = Ensure-Api "cb-window-test" "test" $failPayload
    $rateCircuitApi = Ensure-Api "cb-429-test" "test" $failPayload
    $paramCircuitApi = Ensure-Api "cb-param-test" "test" $failPayload
    $isolateOk = Ensure-Api "cb-isolate-ok" "test" $goodPayload
    $recoverApi = Ensure-TwoVersionApi "cb-recover-test" "test" "$PSScriptRoot/api-cb-recover-good.json" "$PSScriptRoot/api-cb-recover-bad.json"
    $halfFailApi = Ensure-TwoVersionApi "cb-halfopen-fail" "test" "$PSScriptRoot/api-cb-recover-good.json" "$PSScriptRoot/api-cb-recover-bad.json"
    $overrideApi = Ensure-Api "qps-override-ok" "test" $qpsOverridePayload
    $ipApi = Ensure-Api "ip-whitelist-test" "test" $ipWhitelistPayload
    $timeoutApi = Ensure-Api "retry-timeout-test" "test" $timeoutPayload

    Write-Host ""
    Write-Host "=== A. QPS limits ===" -ForegroundColor Cyan

    $p = New-BasePolicy
    $p.globalQpsEnabled = $true
    $p.globalQps = 2
    Set-Policy $p
    $s = Invoke-Burst $okApi.Path 4
    Assert-Test "A1 global QPS returns 429" (@($s | Where-Object { $_ -eq "429" }).Count -ge 1) ("codes=$($s -join ',')")
    Start-Sleep -Seconds 1

    $p = New-BasePolicy
    $p.ipQpsEnabled = $true
    $p.ipQps = 1
    Set-Policy $p
    $ip = "10.99." + (Get-Random -Minimum 1 -Maximum 254) + "." + (Get-Random -Minimum 1 -Maximum 254)
    $hdr = @{ "X-Forwarded-For" = $ip }
    Invoke-Api $okApi.Path $hdr | Out-Null
    Start-Sleep -Milliseconds 100
    $r = Invoke-Api $okApi.Path $hdr
    if ($r.Status -ne 429) {
        Invoke-Api $okApi.Path $hdr | Out-Null
        $r = Invoke-Api $okApi.Path $hdr
    }
    Assert-Test "A2 IP QPS returns 429" ($r.Status -eq 429) ("HTTP $($r.Status)")
    Assert-Test "A2 IP QPS message" ($r.Body -match "IP QPS") $r.Body
    Start-Sleep -Seconds 1

    $p = New-BasePolicy
    $p.apiQpsEnabled = $true
    $p.apiQps = 1
    Set-Policy $p
    Invoke-Api $okApi.Path | Out-Null
    $r = Invoke-Api $okApi.Path
    Assert-Test "A3 API QPS returns 429" ($r.Status -eq 429) ("HTTP $($r.Status)")
    Assert-Test "A3 API QPS message" ($r.Body -match "QPS") $r.Body
    Start-Sleep -Seconds 1

    $p = New-BasePolicy
    $p.apiQpsEnabled = $true
    $p.apiQps = 100
    Set-Policy $p
    Start-Sleep -Milliseconds 200
    $s = Invoke-Burst $overrideApi.Path 3
    $r = Invoke-Api $overrideApi.Path
    Assert-Test "A4 per-API QPS override (apiQps=1)" ((@($s | Where-Object { $_ -eq "429" }).Count -ge 1) -or ($r.Status -eq 429)) ("codes=$($s -join ',') last=$($r.Status)")

    $p = New-BasePolicy
    $p.apiQpsEnabled = $true
    $p.apiQps = 1
    Set-Policy $p
    $since = Get-MaxLogId "qps-test-ok"
    Invoke-Api $okApi.Path | Out-Null
    Invoke-Api $okApi.Path | Out-Null
    Start-Sleep -Seconds 2
    $logs = Get-NewLogs "qps-test-ok" $since
    $rateLogs = Count-Logs $logs "RATE_LIMITED"
    Assert-Test "A5 rate limit access log status" ($rateLogs -ge 1) ("RATE_LIMITED=$rateLogs")

    Write-Host ""
    Write-Host "=== B. Circuit breaker ===" -ForegroundColor Cyan

    $p = New-BasePolicy
    $p.circuitEnabled = $true
    $p.circuitMinCalls = 5
    $p.circuitWaitSec = 5
    Set-Policy $p

    Open-Circuit $failApi.Path 6
    $blocked = Invoke-Api $failApi.Path
    Assert-Test "B1 circuit opens with 503" ($blocked.Status -eq 503) ("HTTP $($blocked.Status)")

    $since = Get-MaxLogId "circuit-test"
    Invoke-Api $failApi.Path | Out-Null
    Start-Sleep -Seconds 3
    $logs = Get-NewLogs "circuit-test" $since
    Assert-Test "B2 circuit blocked log CIRCUIT_OPEN" ((Count-Logs $logs "CIRCUIT_OPEN") -eq 1) ("new_logs=$(@($logs).Count)")

    $ok = Invoke-Api $isolateOk.Path
    Assert-Test "B3 circuit isolated per apiCode" ($ok.Status -eq 200) ("isolate HTTP $($ok.Status)")

    $p = New-BasePolicy
    $p.circuitEnabled = $false
    Set-Policy $p
    $disabled = Invoke-Api $windowApi.Path
    Assert-Test "B4 circuit disabled no 503" ($disabled.Status -ne 503) ("HTTP $($disabled.Status)")

    $p = New-BasePolicy
    $p.circuitEnabled = $true
    $p.circuitMinCalls = 20
    $p.apiQpsEnabled = $true
    $p.apiQps = 1
    Set-Policy $p
    1..12 | ForEach-Object { Invoke-Api $rateCircuitApi.Path | Out-Null }
    $since = Get-MaxLogId "cb-429-test"
    1..3 | ForEach-Object { Start-Sleep -Milliseconds 800; Invoke-Api $rateCircuitApi.Path | Out-Null }
    Start-Sleep -Seconds 2
    $logs = Get-NewLogs "cb-429-test" $since
    $errorCount = Count-Logs $logs "ERROR"
    $last = Invoke-Api $rateCircuitApi.Path
    Assert-Test "B5 429 not counted toward circuit" ($errorCount -lt 5 -and $last.Status -ne 503) ("ERROR=$errorCount last=$($last.Status)")

    $noPage = $base + "/api/data/v1/test/cb-param-test?id=1"
    $p = New-BasePolicy
    $p.circuitEnabled = $true
    $p.circuitMinCalls = 5
    Set-Policy $p
    $since = Get-MaxLogId "cb-param-test"
    1..5 | ForEach-Object { Invoke-Api $noPage | Out-Null }
    $last = Invoke-Api $paramCircuitApi.Path
    Assert-Test "B6 param error not counted (no 503 from validation only)" ($last.Status -ne 503) ("last=$($last.Status)")

    $p = New-BasePolicy
    $p.circuitEnabled = $true
    $p.circuitMinCalls = 5
    Set-Policy $p
    $hdr403 = @{ "X-Forwarded-For" = "192.168.1.99" }
    $since = Get-MaxLogId "ip-whitelist-test"
    1..5 | ForEach-Object { Invoke-Api $ipApi.Path $hdr403 | Out-Null }
    $last = Invoke-Api $ipApi.Path $hdr403
    Assert-Test "B7 IP 403 not counted toward circuit" ($last.Status -eq 403) ("last=$($last.Status)")

    $null = Publish-Version $recoverApi.ApiId 2
    $p = New-BasePolicy
    $p.circuitEnabled = $true
    $p.circuitMinCalls = 5
    $p.circuitWaitSec = 5
    Set-Policy $p
    Open-Circuit $recoverApi.PathBad 6
    $blocked = Invoke-Api $recoverApi.PathBad
    Assert-Test "B8 half-open setup circuit open" ($blocked.Status -eq 503) ("HTTP $($blocked.Status)")
    $null = Publish-Version $recoverApi.ApiId 1
    Start-Sleep -Seconds 6
    $recovered = Invoke-Api $recoverApi.PathGood
    $again = Invoke-Api $recoverApi.PathGood
    Assert-Test "B9 half-open success recovers circuit" ($recovered.Status -eq 200 -and $again.Status -eq 200) ("first=$($recovered.Status) second=$($again.Status)")

    $null = Publish-Version $halfFailApi.ApiId 2
    $p = New-BasePolicy
    $p.circuitEnabled = $true
    $p.circuitMinCalls = 5
    $p.circuitWaitSec = 5
    Set-Policy $p
    Open-Circuit $halfFailApi.PathBad 6
    Invoke-Api $halfFailApi.PathBad | Out-Null | Out-Null
    Start-Sleep -Seconds 6
    $half = Invoke-Api $halfFailApi.PathBad
    $reopen = Invoke-Api $halfFailApi.PathBad
    Assert-Test "B10 half-open failure re-opens" ($half.Status -eq 400 -and $reopen.Status -eq 503) ("half=$($half.Status) reopen=$($reopen.Status)")

    if (-not $SkipSlow) {
        Write-Host ""
        Write-Host "=== B11 rolling 60s window (slow ~65s) ===" -ForegroundColor Cyan
        $p = New-BasePolicy
        $p.circuitEnabled = $true
        $p.circuitMinCalls = 5
        $p.circuitWaitSec = 5
        Set-Policy $p
        1..3 | ForEach-Object { Invoke-Api $windowApi.Path | Out-Null }
        Write-Host "waiting 62s for 1-minute window to expire..."
        Start-Sleep -Seconds 62
        1..3 | ForEach-Object { Invoke-Api $windowApi.Path | Out-Null }
        $after = Invoke-Api $windowApi.Path
        Assert-Test "B11 old failures expired (400 not 503)" ($after.Status -eq 400) ("HTTP $($after.Status) after window roll")
    } else {
        Write-Host ""
        Write-Host "=== B11 rolling window skipped (-SkipSlow) ===" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "=== C. Retry ===" -ForegroundColor Cyan

    $p = New-BasePolicy
    $p.circuitEnabled = $false
    $p.retryEnabled = $false
    Set-Policy $p
    $since = Get-MaxLogId "circuit-test"
    $r = Invoke-Api $failApi.Path
    Start-Sleep -Seconds 2
    $logs = Get-NewLogs "circuit-test" $since
    Assert-Test "C1 retry disabled single attempt" ((@($logs).Count -eq 1) -and $r.Status -eq 400) ("logs=$(@($logs).Count) http=$($r.Status)")

    $p = New-BasePolicy
    $p.circuitEnabled = $false
    $p.retryEnabled = $true
    $p.retryMaxAttempts = 2
    $p.retryIntervalMs = 200
    Set-Policy $p
    $since = Get-MaxLogId "circuit-test"
    $r = Invoke-Api $failApi.Path
    Start-Sleep -Seconds 2
    $logs = Get-NewLogs "circuit-test" $since
    Assert-Test "C2 SQL error not retried" ((@($logs).Count -eq 1) -and $r.Status -eq 400) ("logs=$(@($logs).Count) http=$($r.Status)")

    $p = New-BasePolicy
    $p.circuitEnabled = $false
    $p.retryEnabled = $true
    $p.retryMaxAttempts = 2
    $p.retryIntervalMs = 300
    Set-Policy $p
    $since = Get-MaxLogId "retry-timeout-test"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $r = Invoke-Api $timeoutApi.Path
    $sw.Stop()
    Start-Sleep -Seconds 2
    $logs = Get-NewLogs "retry-timeout-test" $since
    Assert-Test "C3 query timeout not retried (BusinessException)" (@($logs).Count -eq 1) ("logs=$(@($logs).Count) ms=$($sw.ElapsedMilliseconds)")
    Assert-Test "C3 timeout single attempt duration < 2500ms" ($sw.ElapsedMilliseconds -lt 2500) ("ms=$($sw.ElapsedMilliseconds)")

} finally {
    if ($savedPolicy) {
        Write-Host ""
        Write-Host "=== restore policy ===" -ForegroundColor Cyan
        Set-Policy $savedPolicy
    }
}

Write-Host ""
Write-Host "=== summary: $passed passed, $failed failed ===" -ForegroundColor Cyan
if ($failed -gt 0) {
    exit 1
}
Write-Host "ALL PASSED" -ForegroundColor Green
