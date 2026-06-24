# SQL API Gateway

Doris / ClickHouse 动态 SQL API 网关。通过管理台配置 SQL 模板与数据源，发布后自动生成 REST 数据接口；内置限流、单 API 熔断、版本发布、主题审批、API Key 鉴权与操作审计。

**License:** [Apache License 2.0](LICENSE) · Copyright 2026 [knowcai](NOTICE)

---

## 中文

### 功能概览

#### 连接串管理

- 支持 **Doris**（MySQL 协议）与 **ClickHouse**（HTTP / Native 协议）
- 连接池：最小空闲、最大连接、连接超时
- ClickHouse 可选 **HTTP 压缩**（降低大结果集带宽）
- Doris 可配置查询超时
- **只读**开关：开启后仅允许 `SELECT / WITH / SHOW / DESC / EXPLAIN`
- 连接测试；超级管理员可直接增删改，普通用户变更走审批

#### 主题与审批

- 每个 API / 连接串归属一个**主题**
- 超级管理员在「主题管理」中指定**主题管理员**与**普通成员**（两种角色互斥）
- 主题管理员可维护本主题成员；普通成员可提交新建/修改/发布/暂停/恢复等变更
- **任一主题管理员或超级管理员审批通过即可生效**（无多级审批链）
- 主题管理员自己提交的变更，需由其他主题管理员或超级管理员审批

#### API / SQL 管理

- **API 定义**：编码、名称、主题、描述
- **版本管理**（v1、v2…），每版本独立配置：
  - 数据源、SQL 模板（`:参数名` 占位符）
  - 响应模式：**分页（PAGE）**
  - 响应配置：超时、IP 白名单、单接口 QPS 覆盖、单页最大条数、最大偏移量
- **发布流程**：
  - 仅 **已发布** 版本对外服务
  - 发布新版本时，旧已发布版本自动 **废弃**；同一 API 同时只有一个有效发布版
  - 若该 API 有进行中请求，拒绝发布（避免切换冲突）
- 管理页 **上下分栏**：上方 API 列表，下方版本列表

#### 调用方（API Key）

- 超级管理员在「调用方管理」创建调用方，绑定可访问的 API 列表
- 动态数据接口 **必须** 携带 API Key：
  - `X-Api-Key: <key>` 或 `Authorization: Bearer <key>`
- 访问日志记录调用方名称；无 Key 或 Key 无效返回 **401**

#### 动态数据 API

```
GET/POST /api/data/v{version}/{theme}/{apiCode}
```

示例：

```
GET /api/data/v1/1/revenue?dt=2024-01-01&page=1&pageSize=20
X-Api-Key: gw_your_api_key
```

**分页参数（query，必填）：**

| 参数 | 说明 |
|------|------|
| `page` | 页码，从 1 开始 |
| `pageSize` | 每页条数，不超过版本配置的「单页最大条数」 |

**版本访问规则：**

| 场景 | 能否请求 |
|------|----------|
| 从未发布 | 否 |
| URL 指定草稿版本 | 否 |
| v2 已发布，v1 已废弃 | 仅 v2 可访问 |
| 已发布且 Key 已授权 | 是 |

#### API 限流与熔断

针对 `/api/data/**`，三层限流（可独立开关）：

| 层级 | 说明 | 默认 |
|------|------|------|
| 全局 QPS | 整网关 | 1000 |
| 单 IP QPS | 按客户端 IP | 100 |
| 单 API QPS | 按 `apiCode` | 50 |

- 超限返回 **429**，不计入熔断
- 版本配置中的 `apiQps` 可覆盖单接口限流

**单 API 熔断（按 apiCode 隔离）：**

- 状态：`CLOSED` → `OPEN` → `HALF_OPEN`
- 失败率在 **最近 1 分钟滚动窗口** 内计算
- 仅统计已进入 SQL 执行的成功/失败；429、403、参数错误 **不计入**
- 默认：最少 20 次、失败率 ≥ 50% 触发，等待 30 秒后半开试探
- 熔断中返回 **503**，可配置 fallback JSON

**SQL 重试：** 可开关；默认最多 2 次、间隔 500ms；业务异常与查询超时不重试

#### 访问监控

- 异步记录：IP、调用方、参数、行数、字节数、耗时、状态
- 状态：`SUCCESS`、`ERROR`、`RATE_LIMITED`、`CIRCUIT_OPEN`、`FORBIDDEN` 等

#### 用户与权限（JWT）

管理端 `/admin/**` 需登录。

| 角色 | 权限 |
|------|------|
| **SUPER_ADMIN** | 用户/主题/调用方/连接串/策略全权限；变更可直接生效或审批任意待办 |
| **API_EDITOR** | 加入主题后可编辑该主题下 API、连接串（变更需审批） |
| **API_VIEWER** | 只读查看 |

- 首次启动自动创建默认管理员：`admin` / `admin123`（见 `gateway.auth` 配置）
- Token 默认有效期 24 小时

#### 操作审计

记录管理端关键操作：登录、增删改、发布、废弃等；含操作人、时间、资源 JSON。

### 环境要求

- JDK 21
- Node.js 18+
- **PostgreSQL**（元数据库，存配置与日志，非 Doris/ClickHouse 业务库）

### 快速启动（Windows）

**1. 后端**

```powershell
cd d:\claudecode\java\api
mvn spring-boot:run
```

后端：http://localhost:8088

**2. 前端**

```powershell
cd d:\claudecode\java\api\frontend
npm install
npm run dev
```

前端：http://localhost:5173 → `admin` / `admin123`

**一键脚本（可选）：**

```powershell
.\start-dev.ps1
```

### 元数据库配置

默认开发配置见 `src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://192.168.31.100:5432/vectordb
    username: ${GATEWAY_DB_USER:root}
    password: ${GATEWAY_DB_PASSWORD:root}
```

可通过环境变量 `GATEWAY_DB_USER` / `GATEWAY_DB_PASSWORD` 覆盖。

**数据安全：** Flyway 仅执行 **增量迁移**，不会删除或重置元数据库中的 API、连接串、用户等业务数据。迁移失败时记录警告并跳过，不阻塞启动。

### 典型使用流程

1. **主题管理** — 创建主题，指定管理员与成员
2. **连接串管理** — 添加 Doris / ClickHouse，测试连接
3. **API / SQL** — 新建 API → 新建版本 → 发布（非超管需审批）
4. **调用方管理** — 创建 API Key 并授权 API
5. **限流熔断** — 按需调整全局 / IP / 单 API 策略
6. **访问监控 / 操作审计** — 查看调用与变更记录

### 单元测试

```powershell
mvn test
```

覆盖主题审批流、SQL 安全校验等。测试使用 `test` profile 与嵌入式 PostgreSQL，**不影响**开发元数据库。

### 目录结构

```
api/
├── LICENSE / NOTICE
├── src/main/java/com/apigateway/   # 后端
├── src/main/resources/db/migration/  # Flyway 迁移
├── src/test/                       # 单元 / 集成测试
├── frontend/                       # Vue3 + Element Plus 管理台
├── scripts/bootstrap-dev.ps1       # 可选：开发环境示例数据初始化
├── docker-compose.yml
└── README.md
```

### 主要配置（application.yml）

```yaml
gateway:
  auth:
    jwt-secret: "..."                    # 生产环境务必修改
    jwt-expiration-hours: 24
    default-admin-username: admin
    default-admin-password: admin123
  consumer:
    bootstrap-name: 开发默认调用方         # 可选：首次启动创建默认 Key
    bootstrap-key: gw_dev_change_me_in_production
```

---

## English

### Overview

SQL API Gateway exposes **read-only SQL** on **Doris** and **ClickHouse** as versioned REST APIs. An admin UI manages datasources, APIs, themes, approvals, API keys, rate limits, circuit breakers, access logs, and audit trails.

**License:** [Apache License 2.0](LICENSE) · Copyright 2026 knowcai

### Features

#### Datasource management

- **Doris** (MySQL protocol) and **ClickHouse** (HTTP / Native JDBC)
- Connection pool tuning, connection test, read-only enforcement
- ClickHouse HTTP **compression** optional
- Super admins edit directly; other users submit changes for **approval**

#### Themes & approval

- Each API and datasource belongs to a **theme**
- Super admin assigns **theme admins** and **members** (roles are mutually exclusive)
- **One approval** from any theme admin or super admin is enough (no multi-step chain)
- Changes by a theme admin require approval from another theme admin or super admin

#### API / SQL management

- API code, name, theme, description
- **Versioned** SQL templates with `:named` parameters
- **PAGE** response mode with timeout, IP allowlist, per-API QPS override, max page size / offset
- **Publish** workflow: only one published version per API; in-flight requests block publish

#### Consumers (API keys)

- Super admin creates consumers and grants API access
- Dynamic endpoints **require** `X-Api-Key` or `Authorization: Bearer <key>`
- Invalid or missing key → **401**

#### Dynamic data API

```
GET/POST /api/data/v{version}/{theme}/{apiCode}?page=1&pageSize=20
Header: X-Api-Key: <key>
```

Published version in URL is mandatory; deprecated versions are rejected.

#### Rate limiting & circuit breaker

- Global, per-IP, and per-API QPS (429 when exceeded; not counted toward breaker)
- Per-**apiCode** circuit breaker with 1-minute rolling window
- Configurable SQL retry for transient connection errors only

#### Access logs & audit

- Async access logs for every dynamic call
- Admin audit log for configuration changes

#### Roles (JWT for `/admin/**`)

| Role | Capabilities |
|------|----------------|
| SUPER_ADMIN | Full access; direct changes or approve any pending item |
| API_EDITOR | Edit theme resources (subject to approval) |
| API_VIEWER | Read-only |

Default admin on first boot: `admin` / `admin123`

### Requirements

- JDK 21, Node.js 18+
- PostgreSQL for **metadata** (not your Doris/ClickHouse data)

### Quick start

```powershell
# Backend
mvn spring-boot:run          # http://localhost:8088

# Frontend
cd frontend && npm install && npm run dev   # http://localhost:5173
```

Or run `.\start-dev.ps1` on Windows.

### Metadata database

Configure `spring.datasource` in `application-dev.yml` or via `GATEWAY_DB_USER` / `GATEWAY_DB_PASSWORD`.

**Data safety:** Flyway runs **incremental migrations only**. It does **not** truncate or reset existing gateway metadata (APIs, datasources, users, etc.). Failed migrations are logged and skipped without blocking startup.

### Typical workflow

1. Create a **theme** and assign admins / members  
2. Add **datasources** and test connections  
3. Define **APIs**, create versions, **publish** (approval if not super admin)  
4. Create **consumers** and API keys  
5. Tune **rate limits / circuit breaker**  
6. Monitor **access logs** and **audit**  

### Unit tests

```powershell
mvn test
```

Uses embedded PostgreSQL under the `test` profile; does not touch your dev metadata database.

### Project layout

```
api/
├── LICENSE / NOTICE
├── src/main/          # Spring Boot backend
├── src/test/          # Unit & integration tests (kept)
├── frontend/          # Vue 3 admin UI
├── scripts/bootstrap-dev.ps1   # optional sample data bootstrap
└── README.md
```

### Configuration snippet

```yaml
gateway:
  auth:
    jwt-secret: "change-me-in-production"
    default-admin-username: admin
    default-admin-password: admin123
  consumer:
    bootstrap-key: gw_dev_change_me_in_production   # optional dev API key
```

---

## License

This project is licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for attribution.
