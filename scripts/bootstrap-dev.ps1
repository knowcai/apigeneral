# Bootstrap: users, theme, ClickHouse/Doris datasources, sample APIs
$ErrorActionPreference = "Stop"
$base = "http://localhost:8088"
$hostIp = "192.168.31.100"

function Login-Admin {
    $resp = Invoke-RestMethod -Method Post -Uri "$base/admin/auth/login" `
        -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
    return @{ Authorization = "Bearer $($resp.data.token)" }
}

function Post-Json($headers, $url, $obj) {
    $json = $obj | ConvertTo-Json -Depth 10 -Compress
    return Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType "application/json; charset=utf-8" -Body $json
}

function Put-Json($headers, $url, $obj) {
    $json = $obj | ConvertTo-Json -Depth 10 -Compress
    return Invoke-RestMethod -Method Put -Uri $url -Headers $headers -ContentType "application/json; charset=utf-8" -Body $json
}

function Ensure-User($headers, $username, $password, $displayName, $role) {
    $users = (Invoke-RestMethod -Uri "$base/admin/users" -Headers $headers).data
    $existing = $users | Where-Object { $_.username -eq $username }
    if ($existing) { Write-Host "User exists: $username"; return $existing }
    Write-Host "Create user: $username"
    return (Post-Json $headers "$base/admin/users" @{
        username = $username; password = $password; displayName = $displayName; role = $role; enabled = $true
    }).data
}

function Test-Ds($headers, $body) {
    try {
        Post-Json $headers "$base/admin/datasources/test" $body | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Create-Datasource($headers, $body) {
    $list = (Invoke-RestMethod -Uri "$base/admin/datasources" -Headers $headers).data
    $hit = $list | Where-Object { $_.name -eq $body.name }
    if ($hit) { Write-Host "Datasource exists: $($body.name)"; return $hit }
    Write-Host "Create datasource: $($body.name)"
    return (Post-Json $headers "$base/admin/datasources" $body).data
}

function Ensure-Api($headers, $code, $name, $dsId, $sql, $themeId) {
    $apis = (Invoke-RestMethod -Uri "$base/admin/apis" -Headers $headers).data
    $api = $apis | Where-Object { $_.apiCode -eq $code }
    if (-not $api) {
        Write-Host "Create API: $code"
        $api = (Post-Json $headers "$base/admin/apis" @{
            apiCode = $code; name = $name; themeId = $themeId; description = "Demo API"
        }).data
    }
    $vers = (Invoke-RestMethod -Uri "$base/admin/apis/$($api.id)/versions" -Headers $headers).data
    if (-not $vers -or $vers.Count -eq 0) {
        $ver = (Post-Json $headers "$base/admin/apis/$($api.id)/versions" @{
            datasourceId = $dsId; sqlTemplate = $sql; responseMode = "PAGE"
            responseConfig = @{ timeoutMs = 30000; maxPageSize = 100; maxOffset = 100000 }
        }).data
        Post-Json $headers "$base/admin/apis/versions/$($ver.id)/publish" @{} | Out-Null
        Write-Host "  Published v$($ver.versionNo)"
    }
    return $api
}

$h = Login-Admin
Write-Host "Logged in as admin"

$admin1 = Ensure-User $h "admin1" "admin123" "Theme admin admin1" "API_EDITOR"
$user1  = Ensure-User $h "user1"  "user123"  "Member user1" "API_EDITOR"
$viewer = Ensure-User $h "viewer1" "viewer123" "Viewer viewer1" "API_VIEWER"

$themes = (Invoke-RestMethod -Uri "$base/admin/themes" -Headers $h).data
if (-not $themes -or $themes.Count -eq 0) {
    Write-Host "Create theme"
    $theme = (Post-Json $h "$base/admin/themes" @{
        name = "default-theme"
        description = "Dev demo theme"
        enabled = $true
        members = @(@{ userId = $admin1.id; role = "THEME_ADMIN" })
    }).data
} else {
    $theme = $themes[0]
    Write-Host "Use existing theme: $($theme.name)"
}

Put-Json $h "$base/admin/themes/$($theme.id)/members" @{ userIds = @($user1.id) } | Out-Null
$themeId = $theme.id

$ckCandidates = @(
    @{ username = "default"; password = "clickhouse" },
    @{ username = "default"; password = "" },
    @{ username = "default"; password = "123456" },
    @{ username = "clickhouse"; password = "" }
)
$ckAuth = $ckCandidates[0]
foreach ($c in $ckCandidates) {
    $probe = @{
        name = "probe-ck"; type = "CLICKHOUSE"; host = $hostIp; port = 8123
        databaseName = "default"; username = $c.username; password = $c.password
        themeId = $themeId; env = "dev"; readonly = $true
        defaultParams = @{ protocol = "http"; compress = "true" }
    }
    if (Test-Ds $h $probe) {
        $ckAuth = $c
        Write-Host "ClickHouse auth OK: $($c.username)/$($c.password)"
        break
    }
}

$ck = Create-Datasource $h @{
    name = "ClickHouse-8123"; type = "CLICKHOUSE"; host = $hostIp; port = 8123
    databaseName = "default"; username = $ckAuth.username; password = $ckAuth.password
    themeId = $themeId; env = "dev"; readonly = $true; status = "ACTIVE"
    description = "192.168.31.100 Docker clickhouse"
    defaultParams = @{ protocol = "http"; compress = "true" }
}

$dorisAuth = @{ username = "root"; password = "" }
foreach ($c in @(@{ username = "root"; password = "" }, @{ username = "root"; password = "root" })) {
    $probe = @{
        name = "probe-doris"; type = "DORIS"; host = $hostIp; port = 9030
        databaseName = "gateway_demo"; username = $c.username; password = $c.password
        themeId = $themeId; env = "dev"; readonly = $true
        defaultParams = @{ protocol = "mysql" }
    }
    if (Test-Ds $h $probe) {
        $dorisAuth = $c
        Write-Host "Doris auth OK (gateway_demo): $($c.username)/$($c.password)"
        break
    }
}

$dorisDb = "gateway_demo"
if (-not (Test-Ds $h @{
    name = "probe-doris"; type = "DORIS"; host = $hostIp; port = 9030
    databaseName = $dorisDb; username = $dorisAuth.username; password = $dorisAuth.password
    themeId = $themeId; env = "dev"; readonly = $true
    defaultParams = @{ protocol = "mysql" }
})) {
    $dorisDb = "information_schema"
}
Write-Host "Doris using database: $dorisDb"

$doris = Create-Datasource $h @{
    name = "Doris-9030"; type = "DORIS"; host = $hostIp; port = 9030
    databaseName = $dorisDb; username = $dorisAuth.username; password = $dorisAuth.password
    themeId = $themeId; env = "dev"; readonly = $true; status = "ACTIVE"
    description = "192.168.31.100 Docker doris all-in-one"
    defaultParams = @{ protocol = "mysql" }
}

Ensure-Api $h "ck-health" "ClickHouse health" $ck.id "SELECT 1 AS ok" $themeId | Out-Null
Ensure-Api $h "ck-databases" "ClickHouse databases" $ck.id "SELECT name FROM system.databases ORDER BY name" $themeId | Out-Null
Ensure-Api $h "doris-health" "Doris health" $doris.id "SELECT 1 AS ok" $themeId | Out-Null

$dorisTablesSql = "SELECT TABLE_NAME AS table_name FROM information_schema.tables WHERE TABLE_SCHEMA = 'gateway_demo' ORDER BY TABLE_NAME"
Ensure-Api $h "doris-orders" "Doris tables demo" $doris.id $dorisTablesSql $themeId | Out-Null

Write-Host ""
Write-Host "=== DONE ==="
Write-Host "Theme ID: $themeId"
Write-Host "Users: admin1/admin123 (theme admin), user1/user123 (member), viewer1/viewer123 (viewer)"
Write-Host "Datasources: ClickHouse-8123, Doris-9030"
Write-Host "APIs: ck-health, ck-databases, doris-health"
