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
- Spring WebClient 出站请求
- MyBatis-Plus 3.5.17 Boot 3 starter
- Jackson 2、Caffeine、MySQL Connector/J：版本由 Spring Boot 统一管理
- MySQL 8.4 LTS

### 外部服务与本地控制

- Rainforest Product Data API：获取 Amazon 商品信息
- DeepSeek API：生成结构化分析与口播文案
- Caffeine：缓存商品快照和分析结果
- 公平的 `Semaphore(1)`：单个 backend 实例内 Rainforest 同时最多一个在途调用

### 部署

- Docker Compose v2
- Nginx stable-alpine
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
├─ database/init.sql        MySQL 初始化结构
├─ deploy/nginx/            Nginx 反向代理配置
├─ docs/                    架构、API 和开发计划
├─ compose.yaml             三服务部署编排
└─ .env.example             环境变量模板
```

## 环境变量

仓库本地已经包含一个被 Git 忽略的 `.env`，其中写入了随机生成的 MySQL 应用密码和 root 密码，因此启动容器时不需要再修改数据库参数。`.env.example` 用于重新创建配置时参考。

```dotenv
COMPOSE_PROJECT_NAME=product_assistant
HTTP_PORT=8088
MYSQL_DATABASE=product_assistant
MYSQL_USER=product_app
MYSQL_PASSWORD=随机生成的应用密码
MYSQL_ROOT_PASSWORD=另一组随机 root 密码
SPRING_PROFILES_ACTIVE=local
RAINFOREST_API_KEY=
RAINFOREST_BASE_URL=https://api.rainforestapi.com
DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-pro
```

`.env` 已被 Git 忽略。任何 API key 都不得放入 `frontend` 目录或以 `VITE_` 前缀暴露给浏览器。

## 本地开发

### 后端

需要 Java 21、Maven 3.6.3+ 和可用的 MySQL 8 数据库。先选中目标数据库并执行 `database/init.sql`，再配置数据库及外部 API 环境变量。

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

两个 Dockerfile 都有专用的 `.dockerignore`，源码不会进入对应镜像的构建上下文。

## Docker Compose 部署

后端 JAR 和前端 `dist` 已存在时，在项目根目录直接执行：

```shell
docker compose up --build -d
```

访问地址固定为 `http://localhost:8088/`。Compose 会自动读取项目根目录的 `.env`，无需在命令中传入任何参数。

当前 `.env` 使用 `local` profile，使三个容器在没有外部 API key 时也能正常启动并提供页面和健康检查。要实际提交商品分析，仍然必须在 `.env` 中填写有效的 `RAINFOREST_API_KEY` 和 `DEEPSEEK_API_KEY`；这两类凭据由外部平台签发，无法通过本地随机生成。

MySQL 必须通过 healthcheck 后 backend 才会启动；backend 的 `/actuator/health` 健康后 Nginx 才会启动。外部只能经 Nginx 访问业务 API，Actuator 不由 Nginx 对外代理。

## API

- `POST /api/v1/product-analyses`：提交 Amazon 商品 URL，创建或读取分析。
- `GET /api/v1/product-analyses/{id}`：读取已经保存的分析。

详细契约见 [docs/api.md](docs/api.md)。

## 缓存与数据

- 商品缓存：最多 100 条，写入后 6 小时过期。
- 分析缓存：最多 200 条，写入后 24 小时过期。
- MySQL 保存商品链接、归一化商品信息、Rainforest 原始 JSON、结构化分析和 DeepSeek 原始响应。
- 同一 Amazon 域名和 ASIN 共用商品快照；同一商品、模型和 prompt 版本共用分析结果。

## 本地演示商品特判

当输入以下精确链接时：

```text
https://www.amazon.com/dp/B073JYC4XM?th=1&psc=1
```

后端从随 JAR 打包的 `backend/src/main/java/demo.json` 读取 Rainforest 样例响应，不调用 Rainforest API，也不占用 Rainforest credits。商品归一化、MySQL 保存和 DeepSeek 分析仍按正常业务流程执行；响应中的 `source` 为 `LOCAL_DEMO`。

该精确链接会跳过已有分析缓存并重新调用 DeepSeek，便于使用真实 DeepSeek key 验证 AI 分析效果。其他 Amazon 链接不受影响。

## 常见问题

### 启动时报 API key 缺失

`prod` profile 会拒绝在缺少 `RAINFOREST_API_KEY` 或 `DEEPSEEK_API_KEY` 时启动。默认 `.env` 使用 `local` profile，因此没有外部 API key 也不会阻止容器启动。

### 返回 `RAINFOREST_BUSY`

当前已有 Rainforest 调用正在执行，并且本次请求等待超过 10 秒。稍后重新提交即可。

### 返回 `PRODUCT_DATA_INCOMPLETE`

Rainforest 没有返回标题或商品对象。请确认商品为公开可访问页面，或更换其他 Amazon 商品链接。

### 返回外部服务超时或额度不足

检查 Rainforest 与 DeepSeek 账户状态、额度和网络连通性。日志只记录 ASIN、域名、耗时和请求编号，不会记录 API key。

## 安全边界

- 不跟随用户 URL、不抓取用户 URL、不允许 IP、localhost、凭据或非 443 端口。
- Amazon 站点通过明确白名单验证。
- API 错误只返回安全业务信息和 requestId，不返回堆栈或上游原始响应。
- Actuator 只公开 `health` 和 `info`。
- 当前 `Semaphore(1)` 只约束一个 backend 实例；扩展到多副本前必须替换为分布式限流。

## 关于测试代码

按照本项目当前开发要求，仓库不包含单元测试、集成测试、前端组件测试或端到端测试源码，也不引入对应测试依赖。
