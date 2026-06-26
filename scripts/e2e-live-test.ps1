# Live E2E test against running gateway (port 8088)
$ErrorActionPreference = "Stop"
$Base = "http://localhost:8088"
$Pass = 0; $Fail = 0; $Results = @()

function Assert($name, $cond, $detail = "") {
    if ($cond) {
        $script:Pass++
        $script:Results += [PSCustomObject]@{ Status = "PASS"; Name = $name; Detail = $detail }
        Write-Host "[PASS] $name" -ForegroundColor Green
    } else {
        $script:Fail++
        $script:Results += [PSCustomObject]@{ Status = "FAIL"; Name = $name; Detail = $detail }
        Write-Host "[FAIL] $name - $detail" -ForegroundColor Red
    }
}

function Invoke-Api {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [hashtable]$Headers = @{},
        $Body = $null,
        [switch]$Raw
    )
    $uri = "$Base$Path"
    $params = @{
        Method      = $Method
        Uri         = $uri
        Headers     = $Headers
        ContentType = "application/json"
    }
    if ($Body -ne $null) {
        if ($Body -is [string]) { $params.Body = $Body }
        else { $params.Body = ($Body | ConvertTo-Json -Depth 20 -Compress) }
    }
    try {
        $resp = Invoke-WebRequest @params -UseBasicParsing
        if ($Raw) { return @{ Status = $resp.StatusCode; Body = $resp.Content; Raw = $resp } }
        return @{ Status = $resp.StatusCode; Json = ($resp.Content | ConvertFrom-Json); Body = $resp.Content }
    } catch {
        $r = $_.Exception.Response
        if ($r) {
            $reader = New-Object System.IO.StreamReader($r.GetResponseStream())
            $content = $reader.ReadToEnd()
            $json = $null
            try { $json = $content | ConvertFrom-Json } catch {}
            return @{ Status = [int]$r.StatusCode; Json = $json; Body = $content; Error = $true }
        }
        throw
    }
}

Write-Host "=== SQL API Gateway Live E2E ===" -ForegroundColor Cyan
Write-Host "Target: $Base`n"

# --- Health ---
$h = Invoke-Api -Path "/actuator/health"
Assert "Health check" ($h.Status -eq 200 -and $h.Json.status -eq "UP") "status=$($h.Json.status)"

# --- Auth Security ---
$noAuth = Invoke-Api -Path "/admin/themes"
Assert "Admin without JWT -> 401" ($noAuth.Status -eq 401) "status=$($noAuth.Status)"

$badJwt = Invoke-Api -Path "/admin/themes" -Headers @{ Authorization = "Bearer invalid.token.here" }
Assert "Invalid JWT -> 401" ($badJwt.Status -eq 401) "status=$($badJwt.Status)"

$wrongPwd = Invoke-Api -Method POST -Path "/admin/auth/login" -Body '{"username":"admin","password":"wrongpass"}'
Assert "Wrong password -> 401" ($wrongPwd.Status -eq 401) "status=$($wrongPwd.Status)"

$login = Invoke-Api -Method POST -Path "/admin/auth/login" -Body '{"username":"admin","password":"admin123"}'
Assert "Login success" ($login.Status -eq 200 -and $login.Json.code -eq 0 -and $login.Json.data.token) "code=$($login.Json.code)"
$Token = $login.Json.data.token
$Auth = @{ Authorization = "Bearer $Token" }

$me = Invoke-Api -Path "/admin/auth/me" -Headers $Auth
Assert "Auth /me" ($me.Json.data.username -eq "admin") "user=$($me.Json.data.username)"

# --- Monitoring ---
$dash = Invoke-Api -Path "/admin/monitoring/dashboard" -Headers $Auth
Assert "Dashboard" ($dash.Json.code -eq 0) "code=$($dash.Json.code)"

$runtime = Invoke-Api -Path "/admin/monitoring/runtime" -Headers $Auth
Assert "Runtime metrics" ($runtime.Json.code -eq 0) "code=$($runtime.Json.code)"

# --- Gateway Policy ---
$policy = Invoke-Api -Path "/admin/gateway-policy" -Headers $Auth
Assert "Get gateway policy" ($policy.Json.code -eq 0) "code=$($policy.Json.code)"

# --- Themes ---
$themes = Invoke-Api -Path "/admin/themes" -Headers $Auth
Assert "List themes" ($themes.Json.code -eq 0) "count=$($themes.Json.data.Count)"

# Create test theme (super admin direct)
$ts = Get-Date -Format "yyyyMMddHHmmss"
$themeBody = @{
    name = "E2E测试主题-$ts"
    code = "e2e$ts"
    description = "automated e2e test theme"
    enabled = $true
}
$newTheme = Invoke-Api -Method POST -Path "/admin/themes" -Headers $Auth -Body $themeBody
Assert "Create theme" ($newTheme.Json.code -eq 0) "code=$($newTheme.Json.code)"
$ThemeId = $newTheme.Json.data.id
$ThemeCode = $newTheme.Json.data.code

$themeDetail = Invoke-Api -Path "/admin/themes/$ThemeId" -Headers $Auth
Assert "Get theme detail" ($themeDetail.Json.code -eq 0) "id=$ThemeId"

$impact = Invoke-Api -Path "/admin/themes/$ThemeId/impact" -Headers $Auth
Assert "Theme impact" ($impact.Json.code -eq 0) "code=$($impact.Json.code)"

# --- Datasource types ---
$dsTypes = Invoke-Api -Path "/admin/datasources/types" -Headers $Auth
Assert "Datasource types" ($dsTypes.Json.code -eq 0 -and $dsTypes.Json.data.Count -gt 0) "types=$($dsTypes.Json.data.Count)"

# Create datasource (POSTGRES pointing to metadata DB for test)
$dsBody = @{
    name = "E2E-DS-$ts"
    type = "POSTGRES"
    host = "192.168.31.100"
    port = 5432
    databaseName = "vectordb"
    username = "root"
    password = "root"
    themeId = $ThemeId
    readOnly = $true
    minIdle = 1
    maxPoolSize = 5
    connectionTimeoutMs = 10000
}
$newDs = Invoke-Api -Method POST -Path "/admin/datasources" -Headers $Auth -Body $dsBody
Assert "Create datasource" ($newDs.Json.code -eq 0) "code=$($newDs.Json.code)"
$DsId = $newDs.Json.data.id

$dsDetail = Invoke-Api -Path "/admin/datasources/$DsId" -Headers $Auth
Assert "Datasource no password leak" ($dsDetail.Json.data.passwordConfigured -eq $true -and $null -eq $dsDetail.Json.data.password) "passwordConfigured=$($dsDetail.Json.data.passwordConfigured)"

$dsTest = Invoke-Api -Method POST -Path "/admin/datasources/$DsId/test" -Headers $Auth
Assert "Datasource connection test" ($dsTest.Json.code -eq 0) "code=$($dsTest.Json.code)"

# --- API Definition ---
$apiBody = @{
    apiCode = "e2e_api_$ts"
    name = "E2E API $ts"
    themeId = $ThemeId
    description = "e2e test api"
}
$newApi = Invoke-Api -Method POST -Path "/admin/apis" -Headers $Auth -Body $apiBody
Assert "Create API definition" ($newApi.Json.code -eq 0) "code=$($newApi.Json.code)"
$ApiId = $newApi.Json.data.id
$ApiCode = $newApi.Json.data.apiCode

$verBody = @{
    datasourceId = $DsId
    sqlTemplate = "SELECT 1 AS id, 'e2e' AS tag"
    responseMode = "PAGE"
    timeoutMs = 30000
    maxPageSize = 100
    maxOffset = 10000
}
$newVer = Invoke-Api -Method POST -Path "/admin/apis/$ApiId/versions" -Headers $Auth -Body $verBody
Assert "Create API version" ($newVer.Json.code -eq 0) "code=$($newVer.Json.code)"
$VerId = $newVer.Json.data.id
$VerNo = $newVer.Json.data.versionNo

$pub = Invoke-Api -Method POST -Path "/admin/apis/versions/$VerId/publish" -Headers $Auth
Assert "Publish API version" ($pub.Json.code -eq 0) "code=$($pub.Json.code)"

$endpoint = Invoke-Api -Path "/admin/apis/versions/$VerId/endpoint" -Headers $Auth
Assert "Get endpoint info" ($endpoint.Json.code -eq 0) "code=$($endpoint.Json.code)"

$openapi = Invoke-Api -Path "/admin/apis/versions/$VerId/openapi" -Headers $Auth
Assert "Get OpenAPI doc" ($openapi.Status -eq 200) "status=$($openapi.Status)"

# --- Theme API Key ---
$keyBody = @{ name = "E2E-Key-$ts" }
$newKey = Invoke-Api -Method POST -Path "/admin/themes/$ThemeId/api-key" -Headers $Auth -Body $keyBody
Assert "Create theme API key" ($newKey.Json.code -eq 0 -and $newKey.Json.data.apiKey) "code=$($newKey.Json.code)"
$ApiKey = $newKey.Json.data.apiKey

$keyGet = Invoke-Api -Path "/admin/themes/$ThemeId/api-key" -Headers $Auth
Assert "Get theme API key (masked)" ($keyGet.Json.code -eq 0 -and $keyGet.Json.data.keyPrefix) "prefix=$($keyGet.Json.data.keyPrefix)"

# --- Data API Security ---
$dataUrl = "/api/data/v$VerNo/$ThemeCode/$ApiCode?page=1&pageSize=10"
$noKey = Invoke-Api -Path $dataUrl
Assert "Data API without key -> 401" ($noKey.Status -eq 401) "status=$($noKey.Status)"

$badKey = Invoke-Api -Path $dataUrl -Headers @{ "X-Api-Key" = "gw_invalid_key_xxx" }
Assert "Data API invalid key -> 401" ($badKey.Status -eq 401) "status=$($badKey.Status)"

$dataOk = Invoke-Api -Path $dataUrl -Headers @{ "X-Api-Key" = $ApiKey }
Assert "Data API with valid key -> 200" ($dataOk.Status -eq 200 -and $dataOk.Json.code -eq 0) "code=$($dataOk.Json.code), rows=$($dataOk.Json.data.rows.Count)"

# Bearer auth for data API
$dataBearer = Invoke-Api -Path $dataUrl -Headers @{ Authorization = "Bearer $ApiKey" }
Assert "Data API Bearer key -> 200" ($dataBearer.Status -eq 200 -and $dataBearer.Json.code -eq 0) "code=$($dataBearer.Json.code)"

# Draft version should fail
$draftUrl = "/api/data/v$VerNo/$ThemeCode/${ApiCode}_draft?page=1&pageSize=10"
# skip draft test if no draft

# --- Rate Limit Test ---
$rlPolicy = @{
    globalQpsEnabled = $false
    ipQpsEnabled = $false
    apiQpsEnabled = $true
    apiQps = 1
    circuitEnabled = $false
    retryEnabled = $false
    globalQps = 1000
    ipQps = 100
    circuitFailureRate = 50
    circuitMinCalls = 20
    circuitWaitSec = 30
    retryMaxAttempts = 2
    retryIntervalMs = 500
}
Invoke-Api -Method PUT -Path "/admin/gateway-policy" -Headers $Auth -Body $rlPolicy | Out-Null
Start-Sleep -Milliseconds 200
$r1 = Invoke-Api -Path $dataUrl -Headers @{ "X-Api-Key" = $ApiKey }
$r2 = Invoke-Api -Path $dataUrl -Headers @{ "X-Api-Key" = $ApiKey }
Assert "Rate limit: first request OK" ($r1.Status -eq 200) "status=$($r1.Status)"
Assert "Rate limit: second request -> 429" ($r2.Status -eq 429) "status=$($r2.Status)"

# Reset policy
$defaultPolicy = @{
    globalQpsEnabled = $true; globalQps = 1000
    ipQpsEnabled = $true; ipQps = 100
    apiQpsEnabled = $true; apiQps = 50
    circuitEnabled = $true; circuitFailureRate = 50; circuitMinCalls = 20; circuitWaitSec = 30
    retryEnabled = $true; retryMaxAttempts = 2; retryIntervalMs = 500
}
Invoke-Api -Method PUT -Path "/admin/gateway-policy" -Headers $Auth -Body $defaultPolicy | Out-Null

# --- Consumers (super admin) ---
$consumers = Invoke-Api -Path "/admin/consumers" -Headers $Auth
Assert "List consumers" ($consumers.Json.code -eq 0) "count=$($consumers.Json.data.Count)"

$legacy = Invoke-Api -Path "/admin/consumers/legacy-migration" -Headers $Auth
Assert "Legacy migration info" ($legacy.Json.code -eq 0) "code=$($legacy.Json.code)"

# Bootstrap key test
$bootstrapKey = "gw_dev_change_me_in_production"
$bootData = Invoke-Api -Path $dataUrl -Headers @{ "X-Api-Key" = $bootstrapKey }
Assert "Bootstrap legacy key works" ($bootData.Status -eq 200 -or $bootData.Status -eq 403) "status=$($bootData.Status) (403 if theme-scoped only)"

# --- Access Logs ---
$logs = Invoke-Api -Path "/admin/access-logs?page=0&size=5" -Headers $Auth
Assert "Access logs" ($logs.Json.code -eq 0) "total=$($logs.Json.data.totalElements)"

# --- Audit Logs ---
$audit = Invoke-Api -Path "/admin/audit-logs?page=0&size=5" -Headers $Auth
Assert "Audit logs" ($audit.Json.code -eq 0) "total=$($audit.Json.data.totalElements)"

$auditKeys = Invoke-Api -Path "/admin/audit-logs/theme-api-keys?page=0&size=5" -Headers $Auth
Assert "Audit theme-api-keys" ($auditKeys.Json.code -eq 0) "code=$($auditKeys.Json.code)"

# --- Approval ---
$pending = Invoke-Api -Path "/admin/approvals/pending" -Headers $Auth
Assert "Approval pending list" ($pending.Json.code -eq 0) "code=$($pending.Json.code)"

$myTasks = Invoke-Api -Path "/admin/approvals/my-tasks" -Headers $Auth
Assert "My approval tasks" ($myTasks.Json.code -eq 0) "code=$($myTasks.Json.code)"

$taskCount = Invoke-Api -Path "/admin/approvals/my-tasks/count" -Headers $Auth
Assert "My tasks count" ($taskCount.Json.code -eq 0) "count=$($taskCount.Json.data)"

$history = Invoke-Api -Path "/admin/approvals/history" -Headers $Auth
Assert "Approval history" ($history.Json.code -eq 0) "code=$($history.Json.code)"

# --- Users ---
$users = Invoke-Api -Path "/admin/users" -Headers $Auth
Assert "List users" ($users.Json.code -eq 0) "count=$($users.Json.data.Count)"

# --- API list/detail ---
$apis = Invoke-Api -Path "/admin/apis" -Headers $Auth
Assert "List APIs" ($apis.Json.code -eq 0) "count=$($apis.Json.data.Count)"

$apiDetail = Invoke-Api -Path "/admin/apis/$ApiId" -Headers $Auth
Assert "Get API detail" ($apiDetail.Json.code -eq 0) "id=$ApiId"

$versions = Invoke-Api -Path "/admin/apis/$ApiId/versions" -Headers $Auth
Assert "List API versions" ($versions.Json.code -eq 0) "count=$($versions.Json.data.Count)"

# --- Frontend proxy check ---
try {
    $fe = Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 5
    Assert "Frontend dev server" ($fe.StatusCode -eq 200) "status=$($fe.StatusCode)"
} catch {
    Assert "Frontend dev server" $false $_.Exception.Message
}

# --- Summary ---
Write-Host "`n=== SUMMARY ===" -ForegroundColor Cyan
Write-Host "PASS: $Pass  FAIL: $Fail  TOTAL: $($Pass + $Fail)"
if ($Fail -gt 0) {
    Write-Host "`nFailed tests:" -ForegroundColor Red
    $Results | Where-Object { $_.Status -eq "FAIL" } | Format-Table -AutoSize
    exit 1
}
Write-Host "All tests passed!" -ForegroundColor Green
exit 0
