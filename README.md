# SQL API Gateway

Doris / ClickHouse SQL API 网关（前后端一体 MVP）。

## 环境要求

- JDK 21（Maven 已配置 `D:\jdk-21` 亦可）
- Node.js 18+
- **无需安装 PostgreSQL**：开发环境自动启动嵌入式 PG（端口 15432，首次会下载约 30MB）

## 快速启动（Windows）

### 1. 启动后端

```powershell
cd d:\claudecode\java\api
mvn spring-boot:run
```

后端地址：http://localhost:8088

### 2. 启动前端

```powershell
cd d:\claudecode\java\api\frontend
npm install
npm run dev
```

前端地址：http://localhost:5173

## 使用流程

1. **连接串管理**：添加 Doris（默认 9030）或 ClickHouse（默认 8123）连接，可配置默认参数 JSON
2. **API / SQL**：创建 API 定义 → 新建版本（SQL 模板、分页/分块/流式、白名单 IP）→ 发布
3. **访问监控**：查看每次请求的 IP、调用方、行数、字节数、耗时

## 动态 API 路径

发布后在版本列表点「API 路径」，格式：

```
GET/POST /api/data/v{version}/{theme}/{apiCode}
```

示例：

```
GET /api/data/v1/finance/revenue?dt=2024-01-01&page=1&pageSize=20
Header: X-Consumer-Name: finance-app
```

流式（需版本 `responseMode=STREAM`）：

```
GET /api/data/v1/finance/revenue/stream?dt=2024-01-01
```

## 切换到外部 PostgreSQL

有 Docker 时：

```powershell
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

修改 `application-prod.yml` 中的连接信息即可。

## 目录结构

```
api/
├── src/main/java/com/apigateway/   # Spring Boot 后端
├── frontend/                        # Vue3 管理台
├── docker-compose.yml               # 可选外部 PG
└── README.md
```
