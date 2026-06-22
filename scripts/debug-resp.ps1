$base = "http://localhost:8088"
$login = '{"username":"admin","password":"admin123"}'
$r = Invoke-RestMethod -Method Post -Uri "$base/admin/auth/login" -ContentType "application/json" -Body $login
$h = @{ Authorization = "Bearer $($r.data.token)" }
$code = "debug-resp-$(Get-Random -Maximum 9999)"
$apiBody = (@{ apiCode = $code; name = $code; theme = "test" } | ConvertTo-Json)
$api = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis" -Headers $h -ContentType "application/json" -Body $apiBody).data
$verBody = (@{
    datasourceId = 1; sqlTemplate = "SELECT sleep(3) AS ok WHERE :id = 1"; responseMode = "PAGE"
    responseConfig = @{ maxPageSize = 10; maxOffset = 50; timeoutSec = 1 }
} | ConvertTo-Json -Depth 5)
$ver = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis/$($api.id)/versions" -Headers $h -ContentType "application/json" -Body $verBody).data
Invoke-RestMethod -Method Post -Uri "$base/admin/apis/versions/$($ver.id)/publish" -Headers $h | Out-Null
$url = "$base/api/data/v1/test/" + $code + "?id=1&page=1&pageSize=11"
Write-Host "URL: $url"
curl.exe -s -w "`nHTTP:%{http_code}" $url
