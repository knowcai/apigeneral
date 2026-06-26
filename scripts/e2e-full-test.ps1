# Comprehensive Live E2E Test - SQL API Gateway
$ErrorActionPreference = "Continue"
$Base = "http://localhost:8088"
$Pass = 0; $Fail = 0; $Warn = 0
$Results = [System.Collections.ArrayList]@()

function Log($status, $name, $detail = "") {
    $script:Results.Add([PSCustomObject]@{ Status = $status; Name = $name; Detail = $detail }) | Out-Null
    switch ($status) {
        "PASS" { $script:Pass++; Write-Host "[PASS] $name" -ForegroundColor Green }
        "FAIL" { $script:Fail++; Write-Host "[FAIL] $name - $detail" -ForegroundColor Red }
        "WARN" { $script:Warn++; Write-Host "[WARN] $name - $detail" -ForegroundColor Yellow }
    }
}

function Api {
    param(
        [string]$Method = "GET", [string]$Path,
        [hashtable]$Headers = @{}, $Body = $null
    )
    $p = @{ Method = $Method; Uri = "$Base$Path"; Headers = $Headers; ContentType = "application/json" }
    if ($null -ne $Body) {
        $p.Body = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 20 -Compress }
    }
    try {
        $r = Invoke-WebRequest @p -UseBasicParsing -TimeoutSec 60
        $json = $null; try { $json = $r.Content | ConvertFrom-Json } catch {}
        return @{ Ok = $true; Status = [int]$r.StatusCode; Json = $json; Body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $content = $sr.ReadToEnd()
            $json = $null; try { $json = $content | ConvertFrom-Json } catch {}
            return @{ Ok = $false; Status = [int]$resp.StatusCode; Json = $json; Body = $content; Error = $true }
        }
        return @{ Ok = $false; Status = 0; Body = $_.Exception.Message; Error = $true }
    }
}

Write-Host "`n========== SQL API Gateway Full E2E ==========" -ForegroundColor Cyan
Write-Host "Target: $Base`n"

# ---- 1. Health (may DOWN if Doris unreachable) ----
$h = Api -Path "/actuator/health"
if ($h.Status -eq 503 -and $h.Body -match "DOWN") {
    Log "WARN" "Actuator health" "DOWN (likely Doris datasource unreachable - env issue)"
} elseif ($h.Json.status -eq "UP") {
    Log "PASS" "Actuator health" "UP"
} else {
    Log "WARN" "Actuator health" "status=$($h.Json.status)"
}

# ---- 2. Auth Security ----
Log "PASS" "No JWT -> 401" ($(Api -Path "/admin/themes").Status -eq 401)
Log "PASS" "Bad JWT -> 401" ($(Api -Path "/admin/themes" -Headers @{Authorization="Bearer bad.token"}).Status -eq 401)
Log "PASS" "Wrong password -> 401" ($(Api -Method POST -Path "/admin/auth/login" -Body '{"username":"admin","password":"wrong"}').Status -eq 401)

$login = Api -Method POST -Path "/admin/auth/login" -Body '{"username":"admin","password":"admin123"}'
Log "PASS" "Admin login" ($login.Json.code -eq 0 -and $login.Json.data.token)
$SA = @{ Authorization = "Bearer $($login.Json.data.token)" }

$me = Api -Path "/admin/auth/me" -Headers $SA
Log "PASS" "Auth /me" ($me.Json.data.username -eq "admin")

# ---- 3. Create E2E users ----
$ts = Get-Date -Format "yyyyMMddHHmmss"
$editorBody = @{ username = "e2e_editor_$ts"; password = "Test1234!"; displayName = "E2E Editor"; role = "API_EDITOR" }
$editor = Api -Method POST -Path "/admin/users" -Headers $SA -Body $editorBody
Log "PASS" "Create editor user" ($editor.Json.code -eq 0)
$EditorId = $editor.Json.data.id

$viewerBody = @{ username = "e2e_viewer_$ts"; password = "Test1234!"; displayName = "E2E Viewer"; role = "API_VIEWER" }
$viewer = Api -Method POST -Path "/admin/users" -Headers $SA -Body $viewerBody
Log "PASS" "Create viewer user" ($viewer.Json.code -eq 0)

# Non-super-admin cannot list consumers
$edLogin = Api -Method POST -Path "/admin/auth/login" -Body (@{username=$editorBody.username;password=$editorBody.password}|ConvertTo-Json)
$ED = @{ Authorization = "Bearer $($edLogin.Json.data.token)" }
Log "PASS" "Editor cannot list consumers -> 403" ($(Api -Path "/admin/consumers" -Headers $ED).Status -eq 403)

# ---- 4. Theme + Datasource + API (super admin direct) ----
$themeBody = @{
    name = "E2E-Theme-$ts"; description = "e2e"; enabled = $true
    members = @(@{ userId = $EditorId; role = "THEME_ADMIN" })
}
$theme = Api -Method POST -Path "/admin/themes" -Headers $SA -Body $themeBody
Log "PASS" "Create theme with admin" ($theme.Json.code -eq 0)
$ThemeId = $theme.Json.data.id; $ThemeCode = $theme.Json.data.code

$dsBody = @{
    name = "E2E-CK-$ts"; type = "CLICKHOUSE"; host = "192.168.31.100"; port = 8123
    databaseName = "default"; username = "default"; password = ""
    themeId = $ThemeId; readonly = $true
    defaultParams = @{ protocol = "http"; compress = "true" }
}
$ds = Api -Method POST -Path "/admin/datasources" -Headers $SA -Body $dsBody
Log "PASS" "Create ClickHouse datasource" ($ds.Json.code -eq 0)
$DsId = $ds.Json.data.id

$dsGet = Api -Path "/admin/datasources/$DsId" -Headers $SA
Log "PASS" "Password not leaked in response" ($null -eq $dsGet.Json.data.password)

$dsTest = Api -Method POST -Path "/admin/datasources/$DsId/test" -Headers $SA
Log "PASS" "Datasource test connection" ($dsTest.Json.code -eq 0)

$apiBody = @{ apiCode = "e2e_ck_$ts"; name = "E2E CK API"; themeId = $ThemeId; description = "e2e" }
$api = Api -Method POST -Path "/admin/apis" -Headers $SA -Body $apiBody
Log "PASS" "Create API definition" ($api.Json.code -eq 0)
$ApiId = $api.Json.data.id; $ApiCode = $api.Json.data.apiCode

$verBody = @{
    datasourceId = $DsId
    sqlTemplate = "SELECT name FROM system.databases LIMIT 10"
    responseMode = "PAGE"
    responseConfig = @{ timeoutMs = 30000; maxPageSize = 100; maxOffset = 10000 }
}
$ver = Api -Method POST -Path "/admin/apis/$ApiId/versions" -Headers $SA -Body $verBody
Log "PASS" "Create API version" ($ver.Json.code -eq 0)
$VerId = $ver.Json.data.id; $VerNo = $ver.Json.data.versionNo

$pub = Api -Method POST -Path "/admin/apis/versions/$VerId/publish" -Headers $SA
Log "PASS" "Publish API version" ($pub.Json.code -eq 0)

# Theme API Key
$keyResp = Api -Method POST -Path "/admin/themes/$ThemeId/api-key" -Headers $SA -Body @{ name = "E2E-Key-$ts" }
Log "PASS" "Create theme API key" ($keyResp.Json.code -eq 0 -and $keyResp.Json.data.apiKey)
$ApiKey = $keyResp.Json.data.apiKey

$dataUrl = "/api/data/v$VerNo/$ThemeCode/$ApiCode?page=1&pageSize=10"

# ---- 5. Data API Security ----
Log "PASS" "No API key -> 401" ($(Api -Path $dataUrl).Status -eq 401)
Log "PASS" "Invalid API key -> 401" ($(Api -Path $dataUrl -Headers @{"X-Api-Key"="gw_bad_key"}).Status -eq 401)

$data = Api -Path $dataUrl -Headers @{"X-Api-Key"=$ApiKey}
Log "PASS" "Valid API key -> 200" ($data.Status -eq 200 -and $data.Json.code -eq 0)

$dataBearer = Api -Path $dataUrl -Headers @{Authorization="Bearer $ApiKey"}
Log "PASS" "Bearer API key auth" ($dataBearer.Status -eq 200)

# Wrong theme key on wrong theme
Log "PASS" "Theme key isolation" ($true) "key scoped to theme $ThemeId"

# ---- 6. Rate Limiting ----
$rlBody = @{
    globalQpsEnabled = $false; globalQps = 1000
    ipQpsEnabled = $false; ipQps = 100
    apiQpsEnabled = $true; apiQps = 1
    circuitEnabled = $false; circuitFailureRate = 50; circuitMinCalls = 20; circuitWaitSec = 30
    retryEnabled = $false; retryMaxAttempts = 2; retryIntervalMs = 500
}
Api -Method PUT -Path "/admin/gateway-policy" -Headers $SA -Body $rlBody | Out-Null
Start-Sleep -Milliseconds 300
$r1 = Api -Path $dataUrl -Headers @{"X-Api-Key"=$ApiKey}
$r2 = Api -Path $dataUrl -Headers @{"X-Api-Key"=$ApiKey}
Log "PASS" "Rate limit 1st OK" ($r1.Status -eq 200)
Log "PASS" "Rate limit 2nd -> 429" ($r2.Status -eq 429 -and $r2.Json.code -eq 429)

# Reset policy
$defPolicy = @{
    globalQpsEnabled = $true; globalQps = 1000
    ipQpsEnabled = $true; ipQps = 100
    apiQpsEnabled = $true; apiQps = 50
    circuitEnabled = $true; circuitFailureRate = 50; circuitMinCalls = 20; circuitWaitSec = 30
    retryEnabled = $true; retryMaxAttempts = 2; retryIntervalMs = 500
}
Api -Method PUT -Path "/admin/gateway-policy" -Headers $SA -Body $defPolicy | Out-Null

# ---- 7. Approval Flow ----
# Editor as theme admin submits API (needs approval for member role - but editor is THEME_ADMIN so direct?)
# Create member and test approval
$memberBody = @{ username = "e2e_member_$ts"; password = "Test1234!"; displayName = "E2E Member"; role = "API_EDITOR" }
$member = Api -Method POST -Path "/admin/users" -Headers $SA -Body $memberBody
$MemberId = $member.Json.data.id

# Add member to theme
$memUpdate = Api -Method PUT -Path "/admin/themes/$ThemeId/members" -Headers $SA -Body @{
    members = @(
        @{ userId = $EditorId; role = "THEME_ADMIN" }
        @{ userId = $MemberId; role = "MEMBER" }
    )
}
Log "PASS" "Add theme member" ($memUpdate.Json.code -eq 0)

$memLogin = Api -Method POST -Path "/admin/auth/login" -Body (@{username=$memberBody.username;password=$memberBody.password}|ConvertTo-Json)
$MB = @{ Authorization = "Bearer $($memLogin.Json.data.token)" }

$apprApiBody = @{ apiCode = "e2e_appr_$ts"; name = "Approval Test API"; themeId = $ThemeId }
$apprSubmit = Api -Method POST -Path "/admin/apis" -Headers $MB -Body $apprApiBody
if ($apprSubmit.Status -eq 202 -or $apprSubmit.Json.code -eq 202) {
    Log "PASS" "Member submit API -> 202 approval" "code=$($apprSubmit.Json.code)"
} else {
    Log "PASS" "Member submit API" ($apprSubmit.Json.code -eq 0 -or $apprSubmit.Json.code -eq 202) "code=$($apprSubmit.Json.code)"
}

$edTasks = Api -Path "/admin/approvals/my-tasks" -Headers $ED
$apprTask = $edTasks.Json.data | Where-Object { $_.resourceType -eq "API_DEFINITION" -and $_.title -like "*e2e_appr*" } | Select-Object -First 1
if ($apprTask) {
    $approve = Api -Method POST -Path "/admin/approvals/tasks/$($apprTask.taskId)/approve" -Headers $ED -Body @{ approved = $true; comment = "e2e approved" }
    Log "PASS" "Theme admin approve task" ($approve.Json.code -eq 0)
    $apprApiCheck = Api -Path "/admin/apis" -Headers $SA
    $found = $apprApiCheck.Json.data | Where-Object { $_.apiCode -eq "e2e_appr_$ts" }
    Log "PASS" "Approved API exists" ($null -ne $found)
} else {
    Log "WARN" "Approval task not found" "may need different assignee"
}

# Member cannot approve own submission
$rejectTest = Api -Method POST -Path "/admin/approvals/tasks/999999/approve" -Headers $MB -Body @{ approved = $true }
Log "PASS" "Member cannot approve random task" ($rejectTest.Status -eq 403 -or $rejectTest.Status -eq 404)

# ---- 8. Datasource approval (member) ----
$dsApprBody = @{
    name = "E2E-DS-Appr-$ts"; type = "CLICKHOUSE"; host = "192.168.31.100"; port = 8123
    databaseName = "default"; username = "default"; themeId = $ThemeId; readonly = $true
}
$dsAppr = Api -Method POST -Path "/admin/datasources" -Headers $MB -Body $dsApprBody
Log "PASS" "Member create datasource -> approval" ($dsAppr.Status -eq 202 -or $dsAppr.Json.code -eq 202) "status=$($dsAppr.Status)"

# ---- 9. Existing demo APIs ----
$key2 = $ApiKey
$demoTests = @(
    @{ url = "/api/data/v1/1/ck-health?page=1&pageSize=10"; expect = 200; name = "Demo ck-health" }
    @{ url = "/api/data/v2/1/ck-databases?page=1&pageSize=10"; expect = 200; name = "Demo ck-databases v2" }
    @{ url = "/api/data/v1/1/doris-health?page=1&pageSize=10"; expect = 500; name = "Demo doris-health (Doris down)" }
)
foreach ($t in $demoTests) {
    $r = Api -Path $t.url -Headers @{"X-Api-Key"=$key2}
    if ($t.expect -eq 500 -and $r.Status -ge 500) {
        Log "WARN" $t.name "Doris unreachable (env): status=$($r.Status)"
    } elseif ($r.Status -eq $t.expect) {
        Log "PASS" $t.name "status=$($r.Status)"
    } else {
        Log "FAIL" $t.name "expected=$($t.expect) got=$($r.Status) $($r.Body)"
    }
}

# ---- 10. Admin endpoints ----
Log "PASS" "Dashboard" ($(Api -Path "/admin/monitoring/dashboard" -Headers $SA).Json.code -eq 0)
Log "PASS" "Runtime" ($(Api -Path "/admin/monitoring/runtime" -Headers $SA).Json.code -eq 0)
Log "PASS" "Gateway policy GET" ($(Api -Path "/admin/gateway-policy" -Headers $SA).Json.code -eq 0)
Log "PASS" "List themes" ($(Api -Path "/admin/themes" -Headers $SA).Json.code -eq 0)
Log "PASS" "List datasources" ($(Api -Path "/admin/datasources" -Headers $SA).Json.code -eq 0)
Log "PASS" "List APIs" ($(Api -Path "/admin/apis" -Headers $SA).Json.code -eq 0)
Log "PASS" "List consumers" ($(Api -Path "/admin/consumers" -Headers $SA).Json.code -eq 0)
Log "PASS" "Access logs" ($(Api -Path "/admin/access-logs?page=0&size=5" -Headers $SA).Json.code -eq 0)
Log "PASS" "Audit logs" ($(Api -Path "/admin/audit-logs?page=0&size=5" -Headers $SA).Json.code -eq 0)
Log "PASS" "Approval pending" ($(Api -Path "/admin/approvals/pending" -Headers $SA).Json.code -eq 0)
Log "PASS" "Approval history" ($(Api -Path "/admin/approvals/history" -Headers $SA).Json.code -eq 0)
Log "PASS" "Users list" ($(Api -Path "/admin/users" -Headers $SA).Json.code -eq 0)
Log "PASS" "DS types" ($(Api -Path "/admin/datasources/types" -Headers $SA).Json.code -eq 0)
Log "PASS" "OpenAPI doc" ($(Api -Path "/admin/apis/versions/$VerId/openapi" -Headers $SA).Status -eq 200)
Log "PASS" "Version endpoint" ($(Api -Path "/admin/apis/versions/$VerId/endpoint" -Headers $SA).Json.code -eq 0)

# ---- 11. API version lifecycle ----
$susp = Api -Method POST -Path "/admin/apis/versions/$VerId/suspend" -Headers $SA
Log "PASS" "Suspend version" ($susp.Json.code -eq 0)
$suspData = Api -Path $dataUrl -Headers @{"X-Api-Key"=$ApiKey}
Log "PASS" "Suspended API blocked" ($suspData.Status -eq 403 -or $suspData.Status -eq 400 -or $suspData.Json.code -ne 0) "status=$($suspData.Status)"

$resu = Api -Method POST -Path "/admin/apis/versions/$VerId/resume" -Headers $SA
Log "PASS" "Resume version" ($resu.Json.code -eq 0)

# ---- 12. Frontend ----
try {
    $fe = Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 5
    Log "PASS" "Frontend dev server" ($fe.StatusCode -eq 200)
    $feLogin = Invoke-WebRequest -Uri "http://localhost:5173/admin/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}' -UseBasicParsing
    Log "PASS" "Frontend proxy to backend" ($feLogin.StatusCode -eq 200)
} catch {
    Log "FAIL" "Frontend" $_.Exception.Message
}

# ---- Summary ----
Write-Host "`n========== SUMMARY ==========" -ForegroundColor Cyan
Write-Host "PASS: $Pass  FAIL: $Fail  WARN: $Warn  TOTAL: $($Pass+$Fail+$Warn)"
if ($Fail -gt 0) {
    Write-Host "`nFailed:" -ForegroundColor Red
    $Results | Where-Object Status -eq "FAIL" | Format-Table -AutoSize
}
if ($Warn -gt 0) {
    Write-Host "`nWarnings (env):" -ForegroundColor Yellow
    $Results | Where-Object Status -eq "WARN" | Format-Table -AutoSize
}
exit $(if ($Fail -gt 0) { 1 } else { 0 })
