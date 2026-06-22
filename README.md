# SQL API Gateway

Doris / ClickHouse 动态 SQL API 网关。通过管理台配置 SQL 模板与数据源，发布后自动生成 REST 数据接口；内置限流、单 API 熔断、版本发布、权限与操作审计。

## 功能概览

### 连接串管理

- 支持 **Doris**（MySQL 协议）与 **ClickHouse**（HTTP / Native 协议）
- 连接池参数：最小空闲、最大连接、连接超时
- ClickHouse 可选 **启用压缩**（HTTP 传输结果压缩，降低带宽）
- Doris 可配置查询超时
- 只读开关：开启后仅允许 `SELECT / WITH / SHOW / DESC / EXPLAIN`
- 连接测试、增删改（仅超级管理员可写）

### API / SQL 管理

- **API 定义**：编码、名称、主题（theme）、描述
- **版本管理**：同一 API 多版本（v1、v2…），每版本独立配置：
  - 数据源、SQL 模板（`:参数名` 占位符）
  - 响应模式：分页（PAGE）
  - 响应配置：超时、IP 白名单、单接口 QPS 覆盖、单页最大条数、最大偏移量
- **发布流程**：
  - 仅 `已发布` 版本对外提供服务
  - 发布新版本时，**旧已发布版本自动变为「已废弃」**，同一 API 同时只有一个有效发布版
  - 发布时若该 API 有进行中的请求，会拒绝发布（防止切换版本时冲突）
- 管理页 **上下分栏**：上方 API 列表，下方版本列表，点击 API 即加载版本

### 动态数据 API（公开，无需登录）

路径格式：

```
GET/POST /api/data/v{version}/{theme}/{apiCode}
```

示例：

```
GET /api/data/v1/finance/revenue?dt=2024-01-01&id=123&page=1&pageSize=20
Header: X-Consumer-Name: finance-app   # 可选，用于访问日志
```

**分页参数（必填，走 query）：**

| 参数 | 说明 |
|------|------|
| `page` | 页码，从 1 开始 |
| `pageSize` | 每页条数，不超过版本配置的「单页最大条数」 |

**版本访问规则：**

| 场景 | 能否请求 |
|------|----------|
| 从未发布任何版本 | 否 → `指定版本未发布或不存在` |
| 请求 URL 中版本为草稿 | 否 |
| v2 已发布，v1 已废弃 | v1 路径 **不可** 访问，仅 v2 可用 |
| 请求已发布版本 | 是 |

### API 限流与熔断

针对 **`/api/data/**` 动态接口**，三层限流（可独立开关）：

| 层级 | 说明 | 默认 |
|------|------|------|
| 全局 QPS | 整网关总 QPS | 1000 |
| 单 IP QPS | 按客户端 IP | 100 |
| 单 API QPS | 按 `apiCode` | 50 |

- 超限返回 **429**，不计入熔断统计
- 单 API 可在版本配置中设置 `apiQps` 覆盖全局单接口限流

**单 API 熔断（按 apiCode 隔离）：**

- 状态机：`CLOSED` → `OPEN` → `HALF_OPEN` → 恢复或再次熔断
- 失败率在 **最近 1 分钟滚动窗口** 内计算
- 仅统计 **已进入 SQL 执行** 的成功/失败；429、403、参数校验失败 **不计入**
- 默认：最少 20 次调用、失败率 ≥ 50% 触发熔断，等待 30 秒后半开试探
- 熔断中返回 **503**，可配置 fallback JSON
- 某 API 熔断 **不影响** 其他 API

**SQL 重试：**

- 可开关；默认最多重试 2 次、间隔 500ms
- 仅对连接超时等可重试异常生效；业务异常与查询超时（BusinessException）不重试

### 访问监控

- 异步记录每次动态 API 调用：IP、调用方、参数、行数、字节数、耗时、状态
- 状态包括：`SUCCESS`、`ERROR`、`RATE_LIMITED`、`CIRCUIT_OPEN`、`FORBIDDEN` 等
- 管理台可按 API 编码筛选

### 用户与权限（JWT）

管理端 `/admin/**` 需登录；动态 API `/api/data/**` **无需认证**。

| 角色 | 权限 |
|------|------|
| **SUPER_ADMIN** | 用户管理、连接串/策略写、所有 API 读写 |
| **API_EDITOR** | 查看连接串与策略；**仅创建/编辑自己创建的 API** |
| **API_VIEWER** | 只读查看 API、连接串、策略、日志 |

- 首次启动自动创建默认管理员：`admin` / `admin123`（可在 `application.yml` 的 `gateway.auth` 配置）
- Token 有效期默认 24 小时

### 操作审计

记录管理端关键操作并可在「操作审计」页查询：

- 动作：`LOGIN`、`CREATE`、`UPDATE`、`DELETE`、`PUBLISH`、`DEPRECATE` 等
- 资源：用户、API 定义、API 版本、数据源、网关策略
- 含操作人、时间、资源详情 JSON

---

## 环境要求

- JDK 21
- Node.js 18+
- **无需安装 PostgreSQL**：开发环境自动启动嵌入式 PG（端口 15432，首次会下载约 30MB）

## 快速启动（Windows）

### 1. 启动后端

```powershell
cd d:\claudecode\java\api
mvn spring-boot:run
```

后端：http://localhost:8088

### 2. 启动前端

```powershell
cd d:\claudecode\java\api\frontend
npm install
npm run dev
```

前端：http://localhost:5173 → 使用 `admin` / `admin123` 登录

### 一键脚本（可选）

```powershell
.\start-dev.ps1
```

## 典型使用流程

1. **连接串管理** — 添加 Doris 或 ClickHouse，测试连接
2. **API / SQL** — 新建 API → 新建版本（SQL + 响应配置）→ 发布
3. **API 限流熔断** — 按需调整全局 / IP / 单 API 限流与熔断参数
4. **访问监控** — 查看调用量、错误、限流与熔断记录
5. **操作审计** — 追溯发布、配置变更等操作
6. **用户管理**（超级管理员）— 创建编辑者 / 只读账号

## 切换到外部 PostgreSQL

```powershell
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

修改 `application-prod.yml` 中的数据库连接即可。

## 集成测试脚本

| 脚本 | 说明 |
|------|------|
| `scripts/test-gateway-policy.ps1` | QPS、熔断、重试（加 `-SkipSlow` 跳过 60s 窗口测试） |
| `scripts/test-full-suite.ps1` | 权限、响应配置、熔断、审计、发布拦截 |

需后端已在 8088 运行。

## 目录结构

```
api/
├── src/main/java/com/apigateway/
│   ├── controller/          # 动态 API + 管理端 REST
│   ├── service/             # 业务、限流、熔断、SQL 执行
│   ├── security/            # JWT、角色鉴权
│   └── entity/              # JPA 实体
├── src/main/resources/db/migration/   # Flyway 迁移
├── frontend/                # Vue3 + Element Plus 管理台
├── scripts/                 # 集成测试脚本
├── docker-compose.yml       # 可选外部 PG
└── README.md
```

## 配置项（application.yml）

```yaml
gateway:
  auth:
    jwt-secret: "..."              # 生产环境务必修改
    jwt-expiration-hours: 24
    default-admin-username: admin
    default-admin-password: admin123
```
