# AI 产品分析助手

输入一个公开 Amazon 商品链接，系统通过 Rainforest 获取商品数据，再由 DeepSeek 生成目标用户、使用场景、用户痛点、核心卖点和 150 字以内中文短视频口播文案。

## 技术栈与锁定版本

### 前端

- Node.js 22 LTS（`>=22.12 <23`）
- Vue 3.5.39
- Vite 8.1.4
- `@vitejs/plugin-vue` 6.0.8
- Element Plus 2.14.3
- Axios 1.13.2
- npm 11.4.2

### 后端

- Java 21
- Spring Boot 3.5.16
- Spring MVC 入站接口
- Spring Security 服务端 Session、CSRF 与 BCrypt 密码哈希
- Spring WebClient 出站请求
- MyBatis-Plus 3.5.17 Boot 3 starter
- Flyway、Jackson 2、Caffeine、MySQL Connector/J：版本由 Spring Boot 统一管理
- MySQL 8.4 LTS

### 外部服务与本地控制

- Rainforest Product Data API：获取 Amazon 商品信息
- DeepSeek API：生成结构化分析与口播文案
- Caffeine：缓存商品快照和分析结果
- 公平的 `Semaphore(1)`：单个 backend 实例内 Rainforest 同时最多一个在途调用

### 部署

- Docker Compose v2
- Nginx stable-alpine，生产环境终止 TLS 1.2/1.3
- 三个服务：`nginx`、`backend`、`mysql`

## Spring Boot 与 Rainforest 兼容性

Rainforest 不是 JVM 依赖，而是通过 HTTPS 调用的外部 REST API，因此不会与 Spring Boot、Jackson 或 MyBatis-Plus 产生二进制版本冲突。项目不引入任何 Rainforest 或 DeepSeek 第三方 SDK：

- 用户 URL 只用于解析 Amazon 域名和 ASIN，后端不会直接访问该 URL。
- Rainforest WebClient 只连接配置的 `https://api.rainforestapi.com`。
- Rainforest 响应先读取为 `JsonNode`，再提取稳定业务字段，剩余字段原样保存。
- `pom.xml` 只使用 `mybatis-plus-spring-boot3-starter`，不重复引入原生 MyBatis starter。
- Jackson、Caffeine 和 MySQL Connector/J 不手工覆盖版本，由 Spring Boot dependency management 管理。
- 应用显式运行在 Servlet 模式；WebClient 只作为出站客户端，不使用响应式 Controller。

## 目录结构

```text
.
├─ backend/                 Spring Boot 后端
├─ frontend/                Vue 3 前端与 Nginx 构建文件
├─ deploy/nginx/            Nginx HTTP 配置与生产 HTTPS 模板
├─ deploy/tls/              证书运行时挂载说明（证书和私钥不入库）
├─ docs/                    架构、API 和开发计划
├─ compose.yaml             三服务部署编排
└─ .env.example             环境变量模板
```

## 环境变量

仓库本地已经包含一个被 Git 忽略的 `.env`，其中写入了随机生成的 MySQL 应用密码和 root 密码，因此启动容器时不需要再修改数据库参数。`.env.example` 用于重新创建配置时参考。

```dotenv
COMPOSE_PROJECT_NAME=product_assistant
PUBLIC_SERVER_NAME=example.com
HTTP_PORT=80
HTTPS_PORT=443
MYSQL_DATABASE=product_assistant
MYSQL_USER=product_app
MYSQL_PASSWORD=随机生成的应用密码
MYSQL_ROOT_PASSWORD=另一组随机 root 密码
SPRING_PROFILES_ACTIVE=prod
SESSION_COOKIE_SECURE=true
LOG_LEVEL_ROOT=INFO
RAINFOREST_API_KEY=
RAINFOREST_BASE_URL=https://api.rainforestapi.com
DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-pro
VIDEO_SCRIPT_ACTIVE_KEY_VERSION=v1
VIDEO_SCRIPT_ENCRYPTION_KEYS=v1=32字节密钥的Base64编码
VIDEO_SCRIPT_BACKFILL_ENABLED=false
VIDEO_SCRIPT_BACKFILL_BATCH_SIZE=100
AUTH_RATE_LIMIT_ATTEMPTS=10
AUTH_RATE_LIMIT_WINDOW=10m
POINT_RESERVATION_TIMEOUT=15m
```

`.env` 已被 Git 忽略。外部 API key、AES 密钥和证书私钥都不得放入 `frontend` 目录或以 `VITE_` 前缀暴露给浏览器。`VIDEO_SCRIPT_ENCRYPTION_KEYS` 支持逗号分隔的 `版本=Base64密钥` 映射；每个密钥解码后必须恰好 32 字节，活动版本必须存在于映射中。

AES 密钥是所有 profile 的必需配置，因为本地生成的分析也不得把口播明文写入数据库。首次启动前可用 `openssl rand -base64 32` 生成 32-byte 随机密钥的 Base64 值，再把 `.env` 中的配置写成 `VIDEO_SCRIPT_ENCRYPTION_KEYS=v1=<生成值>`；不要复用示例占位值或把真实值提交到 Git。

## 本地开发

### 后端

需要 Java 21、Maven 3.6.3+ 和可用的 MySQL 8 数据库。应用启动时由 Flyway 自动执行 `backend/src/main/resources/db/migration/` 中的版本化迁移，不再手工执行或维护 `database/init.sql`。

```shell
cd backend
mvn spring-boot:run
```

### 前端

需要 Node.js 22 LTS 和 npm 11。

```shell
cd frontend
npm install
npm run dev
```

Vite 默认把 `/api` 代理至 `http://localhost:8080`。可通过 `VITE_BACKEND_TARGET` 调整开发代理目标，但任何外部 API key 都不能进入该变量。

## 生成本地部署产物

Dockerfile 不会在容器内编译源码。构建镜像前，需要先在本地生成后端 JAR 和前端静态目录：

```shell
cd backend
mvn -DskipTests package

cd ../frontend
npm install
npm run build
```

后端镜像直接复制：

```text
backend/target/product-assistant-0.0.1-SNAPSHOT.jar
```

前端镜像直接复制：

```text
frontend/dist/
```

两个 Dockerfile 都只 `COPY` 上述已构建产物，不会在镜像内复制源码或执行编译；根目录 `.dockerignore` 还会排除 Secret、依赖目录、后端产物和无关文档。

## Docker Compose 部署

后端 JAR 和前端 `dist` 已存在时，在项目根目录直接执行：

```shell
docker compose up --build -d
```

生产部署前将域名证书保存为 `deploy/tls/fullchain.pem`、私钥保存为 `deploy/tls/privkey.pem`，并在 `.env` 设置真实域名和 `SESSION_COOKIE_SECURE=true`。Compose 会映射 80/443，HTTP 自动跳转 HTTPS；访问地址为 `https://PUBLIC_SERVER_NAME/`。证书准备和续期要求见 [deploy/tls/README.md](deploy/tls/README.md)。

生产 profile 要求有效的 Rainforest、DeepSeek、AES-256-GCM 密钥配置和 Secure Session Cookie。仅本地开发可以使用 `local` profile 与 `SESSION_COOKIE_SECURE=false`；此时应通过 Vite 开发代理访问，不应把该配置部署到公开网络。

MySQL 必须通过 healthcheck 后 backend 才会启动；backend 的 `/actuator/health` 健康后 Nginx 才会启动。外部只能经 Nginx 访问业务 API，Actuator 不由 Nginx 对外代理。

## API

- `GET /api/v1/auth/session`：读取登录状态、用户信息、积分并刷新 CSRF Cookie。
- `POST /api/v1/auth/register`：注册邮箱账户，成功后赠送 10 积分。
- `POST /api/v1/auth/login`：创建服务端 Session。
- `POST /api/v1/auth/logout`：注销 Session 并刷新 CSRF 状态。
- `POST /api/v1/product-analyses`：携带 UUID 格式 `X-Idempotency-Key` 提交 Amazon 商品 URL；成功返回分析和剩余积分。
- `GET /api/v1/product-analyses/{id}`：仅允许拥有该分析访问记录的当前用户读取。

详细契约见 [docs/api.md](docs/api.md)。

## 缓存与数据

- 商品缓存：最多 100 条；当前 `product-ttl: 60000000h`，在业务时间尺度上等同不按时间失效。
- 分析缓存：最多 200 条；当前 `analysis-ttl: 24000000000000h`，数据库分析复用也使用同一时效判断，实际主要由容量淘汰或配置变更控制。
- MySQL 保存商品链接、归一化商品信息、Rainforest 原始 JSON、结构化分析和白名单 AI 审计元数据；不持久化 `choices.message.content`，避免口播在审计列重复以明文出现。
- 同一 Amazon 域名和 ASIN 共用商品快照；同一商品、模型和 prompt 版本共用分析结果。
- 分析缓存不保存用户 ID、积分或 Session；用户访问权单独持久化。

## 认证与积分

- 密码以 BCrypt cost 12 单向哈希存储，不使用明文或可逆加密。
- 浏览器使用服务端 Session；Session Cookie 为 HttpOnly、SameSite=Lax，生产环境同时启用 Secure。
- SPA 从 `XSRF-TOKEN` Cookie 读取 CSRF Token，并通过 `X-XSRF-TOKEN` 请求头回传；密码、Session ID 和完整分析响应不写入浏览器持久存储。
- 新用户注册时获得 10 积分并写入 `REGISTER_BONUS` 账本。每次成功返回分析扣 1 积分，包括 `LIVE`、`LOCAL_DEMO`、`DATABASE` 和 `CACHE` 来源。
- 分析开始前原子预占积分；成功时结算并授予访问权，任一外部调用、校验、加密或保存失败时幂等退款。超过 15 分钟仍为 `RESERVED` 的异常记录由定时任务恢复。

## 口播文案加密与密钥轮换

口播文案使用服务端 AES-256-GCM 加密，每条记录使用独立 12-byte IV 和 128-bit Tag。AAD 固定为 `analysis-result:{productSnapshotId}:{model}:{promptVersion}:{keyVersion}`；数据库只保存密文、IV 和密钥版本，密钥仅来自后端 Secret 配置。分析保存和旧数据回填都会清除 AI 原始响应中的内容字段，只保留白名单审计元数据。

轮换时先在 `VIDEO_SCRIPT_ENCRYPTION_KEYS` 保留旧版本并加入新版本，再把 `VIDEO_SCRIPT_ACTIVE_KEY_VERSION` 指向新版本。历史密钥必须保留到所有对应记录完成迁移或生命周期结束，禁止把密钥写入数据库、日志、镜像或前端。

## Flyway 数据库升级

- `V1__baseline_schema.sql` 是原有商品与分析表基线；空数据库依次执行 V1、V2，已有非空数据库在 V1 baseline 后执行 V2。
- `V2__user_points_and_video_script_encryption.sql` 增加用户、积分账本、分析访问权和加密列，并暂时保留可空旧明文列用于迁移。
- 上线前先备份 MySQL volume 或生成一致性 SQL dump。第一阶段发布后设置 `VIDEO_SCRIPT_BACKFILL_ENABLED=true` 完成旧文案加密回填，再核对未加密行和残留明文行都为 0。
- 当前版本故意不包含 V3。只有回填与核对完成并再次备份后，才可在第二阶段版本创建 Flyway V3 删除旧明文列；不得绕过 Flyway 手工执行结构变更。

## 本地演示商品特判

当输入以下精确链接时：

```text
https://www.amazon.com/dp/B073JYC4XM?th=1&psc=1
```

后端从随 JAR 打包的 `backend/src/main/java/demo.json` 读取 Rainforest 样例响应，不调用 Rainforest API，也不占用 Rainforest credits。商品归一化、MySQL 保存和 DeepSeek 分析仍按正常业务流程执行；响应中的 `source` 为 `LOCAL_DEMO`。

该精确链接会跳过已有分析缓存并重新调用 DeepSeek，便于使用真实 DeepSeek key 验证 AI 分析效果。其他 Amazon 链接不受影响。

## 常见问题

### 启动时报 API key 缺失

所有 profile 都会拒绝空白、非法 Base64、非 32-byte 或缺少活动版本的 AES 密钥；`prod` 还会校验 `RAINFOREST_API_KEY`、`DEEPSEEK_API_KEY`、Secure Session Cookie，并拒绝非 HTTPS 的外部 API Base URL，避免凭据明文出站。检查 `.env` 是否仍为占位值、活动密钥版本是否存在，以及 Base64 解码后是否正好 32 字节。

### 返回 `RAINFOREST_BUSY`

当前已有 Rainforest 调用正在执行，并且本次请求等待超过 10 秒。稍后重新提交即可。

### 返回 `PRODUCT_DATA_INCOMPLETE`

Rainforest 没有返回标题或商品对象。请确认商品为公开可访问页面，或更换其他 Amazon 商品链接。

### 返回外部服务超时或额度不足

检查 Rainforest 与 DeepSeek 账户状态、额度和网络连通性。日志只记录 ASIN、域名、耗时和请求编号，不会记录 API key。

## 安全边界

- 不跟随用户 URL、不抓取用户 URL、不允许 IP、localhost、凭据或非 443 端口。
- Amazon 站点通过明确白名单验证。
- Nginx 是公网 TLS 边界，后端 8080 端口不映射到宿主机；API 响应强制 `Cache-Control: no-store`。
- Session/CSRF、登录限流、幂等积分账本和用户分析访问权共同保护业务接口。
- 数据库不保存明文密码；仅口播文案使用带版本的 AES-256-GCM 对称加密。
- API 错误只返回安全业务信息和 requestId，不返回堆栈或上游原始响应。
- Actuator 只公开 `health` 和 `info`。
- 当前 `Semaphore(1)` 只约束一个 backend 实例；扩展到多副本前必须替换为分布式限流。

## 关于测试代码

按照本项目当前开发要求，仓库不包含单元测试、集成测试、前端组件测试或端到端测试源码，也不引入对应测试依赖。
