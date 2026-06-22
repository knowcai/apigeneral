$base = "http://localhost:8088"
$login = '{"username":"admin","password":"admin123"}'
$r = Invoke-RestMethod -Method Post -Uri "$base/admin/auth/login" -ContentType "application/json" -Body $login
$h = @{ Authorization = "Bearer $($r.data.token)" }
$code = "debug-resp2-$(Get-Random -Maximum 9999)"
$apiBody = (@{ apiCode = $code; name = $code; theme = "test" } | ConvertTo-Json)
$api = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis" -Headers $h -ContentType "application/json" -Body $apiBody).data
Write-Host "API id=$($api.id) code=$code"
$verBody = (@{
    datasourceId = 1; sqlTemplate = "SELECT 1 AS ok WHERE :id = 1"; responseMode = "PAGE"
    responseConfig = @{ maxPageSize = 10; maxOffset = 50; timeoutSec = 10 }
} | ConvertTo-Json -Depth 5)
$ver = (Invoke-RestMethod -Method Post -Uri "$base/admin/apis/$($api.id)/versions" -Headers $h -ContentType "application/json" -Body $verBody).data
Write-Host "Version id=$($ver.id) status=$($ver.status)"
$pub = Invoke-RestMethod -Method Post -Uri "$base/admin/apis/versions/$($ver.id)/publish" -Headers $h
Write-Host "Published status=$($pub.data.status)"
$url = "$base/api/data/v1/test/" + $code + "?id=1&page=1&pageSize=10"
Write-Host "URL: $url"
$raw = curl.exe -s -w "__HTTP__:%{http_code}" "$url"
Write-Host $raw
