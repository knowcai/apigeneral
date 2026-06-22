# Response config tests: timeout / maxPageSize / maxOffset
$base = "http://localhost:8088"
$apiCode = "resp-config-test"
$theme = "test"
$failed = $false

function Invoke-Api($url) {
    $raw = curl.exe -s -w "__HTTP__:%{http_code}" $url
    if ($raw -match '__HTTP__:(\d+)\s*$') {
        $status = [int]$Matches[1]
        $body = ($raw -replace '__HTTP__:\d+\s*$', '').Trim()
        return @{ Status = $status; Body = $body }
    }
    return @{ Status = 0; Body = $raw }
}

function Assert-Test($name, $url, $expectStatus, $pattern) {
    $r = Invoke-Api $url
    Write-Host "--- $name ---"
    Write-Host "HTTP $($r.Status): $($r.Body)"
    if ($r.Status -ne $expectStatus -or $r.Body -notmatch $pattern) {
        Write-Host "[FAIL] $name" -ForegroundColor Red
        $script:failed = $true
    } else {
        Write-Host "[PASS] $name" -ForegroundColor Green
    }
}

Write-Host "=== setup ===" -ForegroundColor Cyan
$policy = (Invoke-RestMethod "$base/admin/gateway-policy").data
$policy.circuitEnabled = $false
Invoke-RestMethod -Method Put -Uri "$base/admin/gateway-policy" -ContentType "application/json" -Body ($policy | ConvertTo-Json -Depth 10) | Out-Null

$apis = (Invoke-RestMethod "$base/admin/apis").data
$api = $apis | Where-Object { $_.apiCode -eq $apiCode } | Select-Object -First 1
if (-not $api) {
    $api = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis" -ContentType "application/json" -Body (@{
        apiCode = $apiCode; name = "resp-config-test"; theme = $theme; description = "auto test"; updatedBy = "admin"
    } | ConvertTo-Json)).data
}

$verPayload = Get-Content "$PSScriptRoot/api-response-config-test.json" -Raw | ConvertFrom-Json
$versions = (Invoke-RestMethod "$base/admin/apis/$($api.id)/versions").data
$ver = $versions | Where-Object { $_.versionNo -eq 1 } | Select-Object -First 1
if (-not $ver) {
    $ver = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis/$($api.id)/versions" -ContentType "application/json" -Body ($verPayload | ConvertTo-Json -Depth 10)).data
    Invoke-RestMethod -Method Post -Uri "$base/admin/apis/versions/$($ver.id)/publish?operator=admin" | Out-Null
}

$pathV1 = "$base/api/data/v1/$theme/$apiCode"

Assert-Test "maxPageSize" "${pathV1}?id=1&page=1&pageSize=11" 400 "pageSize"
Assert-Test "maxOffset" "${pathV1}?id=1&page=6&pageSize=10" 400 "50"
Assert-Test "timeout" "${pathV1}?id=1&page=1&pageSize=10" 400 "timeout|超时"

$verPayload.responseConfig.timeoutSec = 10
$ver2 = $versions | Where-Object { $_.versionNo -eq 2 } | Select-Object -First 1
if (-not $ver2) {
    $ver2 = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis/$($api.id)/versions" -ContentType "application/json" -Body ($verPayload | ConvertTo-Json -Depth 10)).data
    Invoke-RestMethod -Method Post -Uri "$base/admin/apis/versions/$($ver2.id)/publish?operator=admin" | Out-Null
}
$pathV2 = "$base/api/data/v2/$theme/$apiCode"
Assert-Test "success" "${pathV2}?id=1&page=1&pageSize=10" 200 "rows"

if ($failed) { exit 1 }
Write-Host "ALL PASSED" -ForegroundColor Green
