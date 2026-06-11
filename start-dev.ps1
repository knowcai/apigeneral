# 一键启动开发环境（开两个窗口）
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root'; mvn spring-boot:run"
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\frontend'; if (-not (Test-Path node_modules)) { npm install }; npm run dev"

Write-Host "后端: http://localhost:8088"
Write-Host "前端: http://localhost:5173"
