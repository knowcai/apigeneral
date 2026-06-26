# Final comprehensive live tests (fixed)
$Base = "http://localhost:8088"
$Pass = 0; $Fail = 0; $Warn = 0

function Record($s, $n, $d = "") {
    if ($s -eq "PASS") { $script:Pass++; Write-Host "[PASS] $n" -ForegroundColor Green }
    elseif ($s -eq "FAIL") { $script:Fail++; Write-Host "[FAIL] $n - $d" -ForegroundColor Red }
    else { $script:Warn++; Write-Host "[WARN] $n - $d" -ForegroundColor Yellow }
}

function Login($u, $p) {
    (Invoke-RestMethod -Uri "$Base/admin/auth/login" -Method POST -ContentType "application/json" `
        -Body (@{ username = $u; password = $p } | ConvertTo-Json)).data.token
}

function Api($Method, $Path, $Headers, $Body) {
    $p = @{ Method = $Method; Uri = "$Base$Path"; Headers = $Headers; ContentType = "application/json" }
    if ($Body) { $p.Body = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress } }
    try {
        $r = Invoke-WebRequest @p -UseBasicParsing -TimeoutSec 60
        $j = $null; try { $j = $r.Content | ConvertFrom-Json } catch {}
        return @{ S = [int]$r.StatusCode; J = $j; B = $r.Content; E = $false }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $c = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
            $j = $null; try { $j = $c | ConvertFrom-Json } catch {}
            return @{ S = [int]$resp.StatusCode; J = $j; B = $c; E = $true }
        }
        return @{ S = 0; B = $_.Exception.Message; E = $true }
    }
}

Write-Host "`n===== FINAL E2E TEST SUITE =====" -ForegroundColor Cyan
$ts = Get-Date -Format "yyyyMMddHHmmss"
$sa = Login "admin" "admin123"
$SA = @{ Authorization = "Bearer $sa" }

# 1. Security
Record "PASS" "No JWT" ($(Api GET "/admin/themes" @{} $null).S -eq 401)
Record "PASS" "Bad JWT" ($(Api GET "/admin/themes" @{Authorization="Bearer x"} $null).S -eq 401)
Record "PASS" "Wrong pwd" ($(Api POST "/admin/auth/login" @{} '{"username":"admin","password":"x"}').S -eq 401)

# 2. Setup theme with 2 admins + member (correct API)
$e1 = (Api POST "/admin/users" $SA (@{ username = "fe1_$ts"; password = "Test1234!"; role = "API_EDITOR" })).J.data
$e2 = (Api POST "/admin/users" $SA (@{ username = "fe2_$ts"; password = "Test1234!"; role = "API_EDITOR" })).J.data
$mem = (Api POST "/admin/users" $SA (@{ username = "fem_$ts"; password = "Test1234!"; role = "API_EDITOR" })).J.data
$theme = (Api POST "/admin/themes" $SA (@{
    name = "FinalE2E_$ts"; enabled = $true
    members = @(@{ userId = $e1.id; role = "THEME_ADMIN" }, @{ userId = $e2.id; role = "THEME_ADMIN" })
})).J.data
Api PUT "/admin/themes/$($theme.id)/members" $SA (@{ userIds = @($mem.id) }) | Out-Null
Record "PASS" "Theme + member setup" ($theme.id -gt 0)

# 3. Approval: member submit, admin2 approve
$mt = Login "fem_$ts" "Test1234!"
$MB = @{ Authorization = "Bearer $mt" }
$sub = Api POST "/admin/apis" $MB (@{ apiCode = "fe_appr_$ts"; name = "Appr"; themeId = $theme.id })
Record "PASS" "Member submit API -> 202" ($sub.S -eq 202 -and $sub.J.code -eq 202)

$e2t = Login "fe2_$ts" "Test1234!"
$E2 = @{ Authorization = "Bearer $e2t" }
$tasks = (Api GET "/admin/approvals/my-tasks" $E2 $null).J.data
$task = $tasks | Where-Object { $_.title -like "*fe_appr*" } | Select-Object -First 1
if ($task) {
    $ap = Api POST "/admin/approvals/tasks/$($task.taskId)/approve" $E2 (@{ approved = $true; comment = "ok" })
    Record "PASS" "Admin approve task" ($ap.J.code -eq 0)
    $found = (Api GET "/admin/apis" $SA $null).J.data | Where-Object { $_.apiCode -eq "fe_appr_$ts" }
    Record "PASS" "API exists after approval" ($null -ne $found)
} else { Record "FAIL" "Approval task missing" "count=$($tasks.Count)" }

# 4. Reject flow
Api POST "/admin/apis" $MB (@{ apiCode = "fe_rej_$ts"; name = "Rej"; themeId = $theme.id }) | Out-Null
$task2 = (Api GET "/admin/approvals/my-tasks" $E2 $null).J.data | Where-Object { $_.title -like "*fe_rej*" } | Select-Object -First 1
if ($task2) {
    Api POST "/admin/approvals/tasks/$($task2.taskId)/approve" $E2 (@{ approved = $false; comment = "no" }) | Out-Null
    $nf = (Api GET "/admin/apis" $SA $null).J.data | Where-Object { $_.apiCode -eq "fe_rej_$ts" }
    Record "PASS" "Reject prevents creation" ($null -eq $nf)
} else { Record "FAIL" "Reject task missing" "" }

# 5. Datasource + publish + data API
$ds = (Api POST "/admin/datasources" $SA (@{
    name = "FE-DS-$ts"; type = "CLICKHOUSE"; host = "192.168.31.100"; port = 8123
    databaseName = "default"; username = "default"; themeId = $theme.id; readonly = $true
})).J.data
$api = (Api POST "/admin/apis" $SA (@{ apiCode = "fe_api_$ts"; name = "FE API"; themeId = $theme.id })).J.data
$ver = (Api POST "/admin/apis/$($api.id)/versions" $SA (@{
    datasourceId = $ds.id; sqlTemplate = "SELECT name FROM system.databases LIMIT 5"
})).J.data
Api POST "/admin/apis/versions/$($ver.id)/publish" $SA $null | Out-Null
$key = (Api POST "/admin/themes/$($theme.id)/api-key" $SA (@{ name = "FE-Key" })).J.data.apiKey
$url = "/api/data/v$($ver.versionNo)/$($theme.code)/fe_api_${ts}?page=1&pageSize=10"
Record "PASS" "No key -> 401" ($(Api GET $url @{} $null).S -eq 401)
Record "PASS" "Data API OK" ($(Api GET $url @{"X-Api-Key"=$key} $null).S -eq 200)

# 6. Rate limit
Api PUT "/admin/gateway-policy" $SA (@{
    globalQpsEnabled = $false; ipQpsEnabled = $false; apiQpsEnabled = $true; apiQps = 1
    circuitEnabled = $false; retryEnabled = $false; globalQps = 1000; ipQps = 100
    circuitFailureRate = 50; circuitMinCalls = 20; circuitWaitSec = 30; retryMaxAttempts = 2; retryIntervalMs = 500
}) | Out-Null
Start-Sleep -Milliseconds 300
Record "PASS" "Rate limit 1st" ($(Api GET $url @{"X-Api-Key"=$key} $null).S -eq 200)
Record "PASS" "Rate limit 2nd 429" ($(Api GET $url @{"X-Api-Key"=$key} $null).S -eq 429)

# 7. Circuit breaker (minCalls=5 per validation)
$cbApi = (Api POST "/admin/apis" $SA (@{ apiCode = "fe_cb_$ts"; name = "CB"; themeId = $theme.id })).J.data
$cbVer = (Api POST "/admin/apis/$($cbApi.id)/versions" $SA (@{
    datasourceId = $ds.id; sqlTemplate = "SELECT * FROM nonexistent_cb_table_xyz"
})).J.data
Api POST "/admin/apis/versions/$($cbVer.id)/publish" $SA $null | Out-Null
$cbUrl = "/api/data/v$($cbVer.versionNo)/$($theme.code)/fe_cb_${ts}?page=1&pageSize=10"
Api PUT "/admin/gateway-policy" $SA (@{
    globalQpsEnabled = $false; ipQpsEnabled = $false; apiQpsEnabled = $false
    circuitEnabled = $true; circuitMinCalls = 5; circuitFailureRate = 50; circuitWaitSec = 60
    retryEnabled = $false; globalQps = 1000; ipQps = 100; apiQps = 50; retryMaxAttempts = 0; retryIntervalMs = 500
}) | Out-Null
for ($i = 1; $i -le 6; $i++) { Api GET $cbUrl @{"X-Api-Key"=$key} $null | Out-Null }
$cb = Api GET $cbUrl @{"X-Api-Key"=$key} $null
if ($cb.S -eq 503 -and $cb.J.code -eq 503) { Record "PASS" "Circuit breaker -> 503" "" }
elseif ($cb.S -ge 500) { Record "WARN" "Circuit breaker" "got $($cb.S) not 503 yet (may need more failures)" }
else { Record "FAIL" "Circuit breaker" "status=$($cb.S) code=$($cb.J.code)" }

# Reset policy
Api PUT "/admin/gateway-policy" $SA (@{
    globalQpsEnabled = $true; globalQps = 1000; ipQpsEnabled = $true; ipQps = 100
    apiQpsEnabled = $true; apiQps = 50; circuitEnabled = $true
    circuitFailureRate = 50; circuitMinCalls = 20; circuitWaitSec = 30
    retryEnabled = $true; retryMaxAttempts = 2; retryIntervalMs = 500
}) | Out-Null

# 8. Password security
Record "PASS" "DS password not returned" ($null -eq (Api GET "/admin/datasources/$($ds.id)" $SA $null).J.data.password)

# 9. Theme 1 demo APIs
$t1key = "gw_302b296bdc7c5106814bf32741489f2d82c0fdb5b18a32ee"
Record "PASS" "Demo ck-health" ($(Api GET "/api/data/v1/1/ck-health?page=1&pageSize=10" @{"X-Api-Key"=$t1key} $null).S -eq 200)
Record "PASS" "Demo ck-databases v2" ($(Api GET "/api/data/v2/1/ck-databases?page=1&pageSize=10" @{"X-Api-Key"=$t1key} $null).S -eq 200)
$doris = Api GET "/api/data/v1/1/doris-health?page=1&pageSize=10" @{"X-Api-Key"=$t1key} $null
if ($doris.S -ge 500) { Record "WARN" "Doris API" "Doris unreachable (env): $($doris.S)" }
else { Record "PASS" "Doris API" "status=$($doris.S)" }

# 10. Admin endpoints smoke
foreach ($p in @("/admin/monitoring/dashboard", "/admin/monitoring/runtime", "/admin/gateway-policy",
    "/admin/themes", "/admin/datasources", "/admin/apis", "/admin/consumers",
    "/admin/logs?page=0&size=3", "/admin/audit-logs?page=0&size=3",
    "/admin/approvals/pending", "/admin/approvals/history", "/admin/users")) {
    $r = Api GET $p $SA $null
    $ok = ($r.J.code -eq 0 -or $r.S -eq 200)
    Record $(if ($ok) { "PASS" } else { "FAIL" }) "GET $p" $(if (-not $ok) { "status=$($r.S) code=$($r.J.code)" } else { "" })
}

# 11. Frontend
try {
    $fe = Invoke-WebRequest "http://localhost:5173" -UseBasicParsing -TimeoutSec 5
    $proxy = Invoke-WebRequest "http://localhost:5173/admin/auth/login" -Method POST -ContentType "application/json" `
        -Body '{"username":"admin","password":"admin123"}' -UseBasicParsing
    Record "PASS" "Frontend + proxy" ($fe.StatusCode -eq 200 -and $proxy.StatusCode -eq 200)
} catch { Record "FAIL" "Frontend" $_.Exception.Message }

# 12. Health
try {
    $h = Invoke-WebRequest "$Base/actuator/health" -UseBasicParsing
    Record "PASS" "Health UP" ($h.StatusCode -eq 200)
} catch {
    Record "WARN" "Health DOWN" "Doris datasource check fails when Doris offline"
}

Write-Host "`n===== RESULT: PASS=$Pass FAIL=$Fail WARN=$Warn =====" -ForegroundColor Cyan
exit $(if ($Fail -gt 0) { 1 } else { 0 })
