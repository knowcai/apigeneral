$base = "http://localhost:8088"
$login = '{"username":"admin","password":"admin123"}'
$r = Invoke-RestMethod -Method Post -Uri "$base/admin/auth/login" -ContentType "application/json" -Body $login
$h = @{ Authorization = "Bearer $($r.data.token)" }
$code = "dbg-pub-" + (Get-Random -Maximum 99999)
$api = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis" -Headers $h -ContentType "application/json" -Body (@{
    apiCode = $code; name = $code; theme = "test"
} | ConvertTo-Json)).data
$v1 = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis/$($api.id)/versions" -Headers $h -ContentType "application/json" -Body (@{
    datasourceId = 1; sqlTemplate = "SELECT pg_sleep(15) AS ok WHERE :id = 1"; responseMode = "PAGE"
    responseConfig = @{ maxPageSize = 100; timeoutSec = 30 }
} | ConvertTo-Json -Depth 5)).data
Invoke-RestMethod -Method Post -Uri "$base/admin/apis/versions/$($v1.id)/publish" -Headers $h | Out-Null
$v2 = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis/$($api.id)/versions" -Headers $h -ContentType "application/json" -Body (@{
    datasourceId = 1; sqlTemplate = "SELECT 2 AS ok WHERE :id = 1"; responseMode = "PAGE"
    responseConfig = @{ maxPageSize = 100; timeoutSec = 10 }
} | ConvertTo-Json -Depth 5)).data
$url = "$base/api/data/v1/test/$code" + "?id=1&page=1&pageSize=10"

foreach ($waitSec in @(2, 4, 6)) {
    Write-Host "`n=== wait $waitSec sec before publish ===" -ForegroundColor Cyan
    $job = Start-Job { param($u) curl.exe -s -o NUL "$u" } -ArgumentList $url
    Start-Sleep -Seconds $waitSec
    $pubFail = $false
    $detail = ""
    try {
        Invoke-RestMethod -Method Post -Uri "$base/admin/apis/versions/$($v2.id)/publish" -Headers $h | Out-Null
        $detail = "publish succeeded"
    } catch {
        $pubFail = $true
        if ($_.ErrorDetails.Message) { $detail = $_.ErrorDetails.Message }
        else { $detail = $_.Exception.Message }
    }
    Wait-Job $job -Timeout 25 | Out-Null
    Remove-Job $job -Force -ErrorAction SilentlyContinue
    Write-Host "blocked=$pubFail detail=$detail"
    if ($pubFail) { break }
    # reset v2 to DRAFT for retry - need new version if already published
    $v2 = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis/$($api.id)/versions" -Headers $h -ContentType "application/json" -Body (@{
        datasourceId = 1; sqlTemplate = "SELECT 3 AS ok WHERE :id = 1"; responseMode = "PAGE"
        responseConfig = @{ maxPageSize = 100; timeoutSec = 10 }
    } | ConvertTo-Json -Depth 5)).data
}
