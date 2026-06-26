# Approval + Circuit Breaker focused tests
$Base = "http://localhost:8088"
$ts = Get-Date -Format "HHmmss"

function Login($user, $pass) {
    (Invoke-RestMethod -Uri "$Base/admin/auth/login" -Method POST -ContentType "application/json" `
        -Body (@{ username = $user; password = $pass } | ConvertTo-Json)).data.token
}

$saToken = Login "admin" "admin123"
$SA = @{ Authorization = "Bearer $saToken" }

Write-Host "=== APPROVAL FLOW ===" -ForegroundColor Cyan
$e1 = Invoke-RestMethod -Uri "$Base/admin/users" -Method POST -Headers $SA -ContentType "application/json" `
    -Body (@{ username = "appr_e1_$ts"; password = "Test1234!"; role = "API_EDITOR" } | ConvertTo-Json)
$e2 = Invoke-RestMethod -Uri "$Base/admin/users" -Method POST -Headers $SA -ContentType "application/json" `
    -Body (@{ username = "appr_e2_$ts"; password = "Test1234!"; role = "API_EDITOR" } | ConvertTo-Json)
$mem = Invoke-RestMethod -Uri "$Base/admin/users" -Method POST -Headers $SA -ContentType "application/json" `
    -Body (@{ username = "appr_m_$ts"; password = "Test1234!"; role = "API_EDITOR" } | ConvertTo-Json)

$theme = Invoke-RestMethod -Uri "$Base/admin/themes" -Method POST -Headers $SA -ContentType "application/json" `
    -Body (@{
        name = "ApprTheme$ts"; enabled = $true
        members = @(
            @{ userId = $e1.data.id; role = "THEME_ADMIN" }
            @{ userId = $e2.data.id; role = "THEME_ADMIN" }
            @{ userId = $mem.data.id; role = "MEMBER" }
        )
    } | ConvertTo-Json -Depth 5)
Write-Host "Theme id=$($theme.data.id) code=$($theme.data.code)"

$memToken = Login "appr_m_$ts" "Test1234!"
$MB = @{ Authorization = "Bearer $memToken" }
try {
    Invoke-WebRequest -Uri "$Base/admin/apis" -Method POST -Headers $MB -ContentType "application/json" `
        -Body (@{ apiCode = "appr_api_$ts"; name = "Appr API"; themeId = $theme.data.id } | ConvertTo-Json) -UseBasicParsing | Out-Null
    Write-Host "FAIL: expected 202" -ForegroundColor Red
} catch {
    $code = [int]$_.Exception.Response.StatusCode
    $sr = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    $body = $sr.ReadToEnd() | ConvertFrom-Json
    if ($code -eq 202 -and $body.code -eq 202) { Write-Host "PASS: Member submit -> 202" -ForegroundColor Green }
    else { Write-Host "FAIL: submit code=$code body=$($body | ConvertTo-Json -Compress)" -ForegroundColor Red }
}

$e2Token = Login "appr_e2_$ts" "Test1234!"
$E2 = @{ Authorization = "Bearer $e2Token" }
$tasks = Invoke-RestMethod -Uri "$Base/admin/approvals/my-tasks" -Headers $E2
$task = $tasks.data | Where-Object { $_.title -like "*appr_api*" } | Select-Object -First 1
if ($task) {
    Write-Host "PASS: Theme admin has pending task taskId=$($task.taskId)" -ForegroundColor Green
    $appr = Invoke-RestMethod -Uri "$Base/admin/approvals/tasks/$($task.taskId)/approve" -Method POST -Headers $E2 -ContentType "application/json" `
        -Body (@{ approved = $true; comment = "ok" } | ConvertTo-Json)
    if ($appr.code -eq 0) { Write-Host "PASS: Approve success" -ForegroundColor Green }
    else { Write-Host "FAIL: approve code=$($appr.code)" -ForegroundColor Red }
    $found = (Invoke-RestMethod -Uri "$Base/admin/apis" -Headers $SA).data | Where-Object { $_.apiCode -eq "appr_api_$ts" }
    if ($found) { Write-Host "PASS: API exists after approval" -ForegroundColor Green }
    else { Write-Host "FAIL: API not found" -ForegroundColor Red }
} else {
    Write-Host "FAIL: No task for theme admin e2, tasks=$($tasks.data.Count)" -ForegroundColor Red
}

# Reject flow
try {
    Invoke-WebRequest -Uri "$Base/admin/apis" -Method POST -Headers $MB -ContentType "application/json" `
        -Body (@{ apiCode = "reject_api_$ts"; name = "Reject API"; themeId = $theme.data.id } | ConvertTo-Json) -UseBasicParsing | Out-Null
} catch {}
$task2 = (Invoke-RestMethod -Uri "$Base/admin/approvals/my-tasks" -Headers $E2).data | Where-Object { $_.title -like "*reject_api*" } | Select-Object -First 1
if ($task2) {
    Invoke-RestMethod -Uri "$Base/admin/approvals/tasks/$($task2.taskId)/approve" -Method POST -Headers $E2 -ContentType "application/json" `
        -Body (@{ approved = $false; comment = "no" } | ConvertTo-Json) | Out-Null
    $notFound = (Invoke-RestMethod -Uri "$Base/admin/apis" -Headers $SA).data | Where-Object { $_.apiCode -eq "reject_api_$ts" }
    if (-not $notFound) { Write-Host "PASS: Rejected API not created" -ForegroundColor Green }
    else { Write-Host "FAIL: Rejected API still exists" -ForegroundColor Red }
}

Write-Host "`n=== CIRCUIT BREAKER ===" -ForegroundColor Cyan
# Create API with failing SQL
$ds = Invoke-RestMethod -Uri "$Base/admin/datasources" -Method POST -Headers $SA -ContentType "application/json" `
    -Body (@{
        name = "CB-DS-$ts"; type = "CLICKHOUSE"; host = "192.168.31.100"; port = 8123
        databaseName = "default"; username = "default"; themeId = $theme.data.id; readonly = $true
    } | ConvertTo-Json)
$api = Invoke-RestMethod -Uri "$Base/admin/apis" -Method POST -Headers $SA -ContentType "application/json" `
    -Body (@{ apiCode = "cb_fail_$ts"; name = "CB Fail"; themeId = $theme.data.id } | ConvertTo-Json)
$ver = Invoke-RestMethod -Uri "$Base/admin/apis/$($api.data.id)/versions" -Method POST -Headers $SA -ContentType "application/json" `
    -Body (@{ datasourceId = $ds.data.id; sqlTemplate = "SELECT * FROM nonexistent_table_xyz_cb" } | ConvertTo-Json)
Invoke-RestMethod -Uri "$Base/admin/apis/versions/$($ver.data.id)/publish" -Method POST -Headers $SA | Out-Null
$key = (Invoke-RestMethod -Uri "$Base/admin/themes/$($theme.data.id)/api-key" -Method POST -Headers $SA -ContentType "application/json" `
    -Body (@{ name = "CB-Key" } | ConvertTo-Json)).data.apiKey

# Set circuit breaker policy
Invoke-RestMethod -Uri "$Base/admin/gateway-policy" -Method PUT -Headers $SA -ContentType "application/json" `
    -Body (@{
        globalQpsEnabled = $false; ipQpsEnabled = $false; apiQpsEnabled = $false
        circuitEnabled = $true; circuitMinCalls = 2; circuitFailureRate = 50; circuitWaitSec = 60
        retryEnabled = $false; globalQps = 1000; ipQps = 100; apiQps = 50
        retryMaxAttempts = 2; retryIntervalMs = 500
    } | ConvertTo-Json) | Out-Null

$url = "/api/data/v$($ver.data.versionNo)/$($theme.data.code)/cb_fail_$ts?page=1&pageSize=10"
for ($i = 1; $i -le 3; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "$Base$url" -Headers @{"X-Api-Key"=$key} -UseBasicParsing -TimeoutSec 30
        Write-Host "Call $i -> $($r.StatusCode)"
    } catch {
        Write-Host "Call $i -> $([int]$_.Exception.Response.StatusCode)"
    }
}
try {
    $r = Invoke-WebRequest -Uri "$Base$url" -Headers @{"X-Api-Key"=$key} -UseBasicParsing -TimeoutSec 30
    Write-Host "After failures: $($r.StatusCode) code=$((($r.Content|ConvertFrom-Json).code))"
} catch {
    $code = [int]$_.Exception.Response.StatusCode
    $body = ([System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())).ReadToEnd() | ConvertFrom-Json
    if ($code -eq 503 -and $body.code -eq 503) { Write-Host "PASS: Circuit open -> 503" -ForegroundColor Green }
    else { Write-Host "Result: HTTP $code code=$($body.code) msg=$($body.message)" -ForegroundColor Yellow }
}

# Reset policy
Invoke-RestMethod -Uri "$Base/admin/gateway-policy" -Method PUT -Headers $SA -ContentType "application/json" `
    -Body (@{
        globalQpsEnabled = $true; globalQps = 1000; ipQpsEnabled = $true; ipQps = 100
        apiQpsEnabled = $true; apiQps = 50; circuitEnabled = $true
        circuitFailureRate = 50; circuitMinCalls = 20; circuitWaitSec = 30
        retryEnabled = $true; retryMaxAttempts = 2; retryIntervalMs = 500
    } | ConvertTo-Json) | Out-Null

Write-Host "`n=== THEME 1 DEMO APIs (with theme key) ===" -ForegroundColor Cyan
$t1key = "gw_302b296bdc7c5106814bf32741489f2d82c0fdb5b18a32ee"
foreach ($path in @("/api/data/v1/1/ck-health?page=1&pageSize=10", "/api/data/v2/1/ck-databases?page=1&pageSize=10")) {
    try {
        $r = Invoke-WebRequest -Uri "$Base$path" -Headers @{"X-Api-Key"=$t1key} -UseBasicParsing -TimeoutSec 30
        Write-Host "PASS: $path -> $($r.StatusCode)" -ForegroundColor Green
    } catch {
        Write-Host "FAIL: $path -> $([int]$_.Exception.Response.StatusCode)" -ForegroundColor Red
    }
}

Write-Host "`nDone."
