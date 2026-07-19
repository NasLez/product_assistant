# AI 产品分析助手 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 开发一个可部署的网页工具，接收公开 Amazon 商品链接，获取并整理商品信息，生成产品分析与 150 字以内中文短视频口播文案。

**Architecture:** 前端采用 Vue 3 单页应用并由 Nginx 提供静态文件；后端采用 Spring MVC，同步编排 Rainforest 和 DeepSeek 两个外部 HTTP API。Rainforest 只通过 Spring WebClient 调用，并由单实例 `Semaphore(1)`、Caffeine 与 MySQL 共同控制并发、成本和重复请求，因此不会把 Rainforest 的任何 SDK 依赖引入 Spring Boot 依赖树。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring MVC、Spring WebClient、MyBatis-Plus 3.5.17、Jackson 2（由 Spring Boot 管理）、Caffeine、MySQL 8.4、Vue 3.5.39、Vite 8.1.4、Element Plus 2.14.3、Axios 1.13.x、Node.js 22 LTS、npm 11、Docker Compose v2、Nginx stable-alpine、Rainforest Product Data API、DeepSeek API。

---

## 1. 范围与硬性约束

### 1.1 已阅读的项目文件

- `readme.md`：确定了 Vue 3、Vite、Element Plus、Axios、Java 21、Spring Boot 3、WebClient、MyBatis-Plus、Jackson、Rainforest、DeepSeek、MySQL 8、Caffeine、`Semaphore(1)`、Docker Compose 和 Nginx。
- `demo.json`：一份有效的 Rainforest `type=product` 响应，共有 `request_info`、`request_parameters`、`request_metadata`、`product`、`brand_store`、`newer_model` 六个顶层字段；`product` 含 46 个直接字段。
- `AI工程师实习生笔试题.pdf`：要求支持不同公开 Amazon 商品链接，输出产品信息、产品分析和 150 字以内中文口播文案；口播开头 5 秒需有钩子；部署环境必须可访问并具有商品泛化能力。

### 1.2 本计划不包含的内容

- 不创建 `src/test`、`tests`、`__tests__`、`*.spec.*`、`*.test.*` 或端到端测试目录。
- 不开发单元测试、集成测试、接口自动化测试、前端组件测试或端到端测试代码。
- 不引入 JUnit、Mockito、Testcontainers、Vitest、Jest、Cypress、Playwright 等测试依赖。
- 允许执行编译、构建、依赖树检查、容器健康检查和人工接口验收；这些仅用于确认交付物可运行，不产生测试代码。
- 首版不实现用户系统、多租户、任务队列、多后端副本、后台管理、支付和商品历史趋势。

## 2. 技术栈版本冲突结论

以下结论以 2026-07-18 可查到的官方稳定版本和项目当前 README 为基准。

| 组合 | 结论 | 处理规则 |
|---|---|---|
| Java 21 + Spring Boot 3.5.16 | 兼容 | Spring Boot 3.5.16 支持 Java 17 至 25；统一编译目标为 Java 21。 |
| Spring Boot 3.5.16 + Spring Framework/Jackson/Caffeine/MySQL Connector | 兼容 | 全部沿用 Spring Boot dependency management，不手工覆盖传递依赖版本。 |
| Spring Boot 3 + MyBatis-Plus 3.5.17 | 兼容 | 只使用 `mybatis-plus-spring-boot3-starter`；禁止同时引入 Boot 2 starter、Boot 4 starter、原生 MyBatis starter 或 `mybatis-spring`。 |
| Spring MVC + WebClient | 兼容 | 入站接口保持 Servlet/MVC；WebClient 仅作出站 HTTP 客户端。显式配置 `spring.main.web-application-type=servlet`，不编写 WebFlux Controller。 |
| Spring Boot + Rainforest | **不存在二进制或依赖版本冲突** | Rainforest 是 HTTPS REST API，不添加任何 Rainforest Java SDK。用 WebClient 请求固定域名，并用 `JsonNode` 容忍字段变化。 |
| Spring Boot + DeepSeek | 不存在二进制依赖冲突 | 不添加 OpenAI/DeepSeek SDK，直接调用兼容的 `/chat/completions` HTTP 接口。 |
| Jackson + Rainforest 动态 JSON | 可兼容但有结构漂移风险 | 只为稳定业务字段建小型 DTO，其余保留为 `JsonNode` 并将原始 JSON 入库。 |
| MySQL 8.4 + MyBatis-Plus | 兼容 | 使用 Boot 管理的 `mysql-connector-j`，金额使用 `BigDecimal/DECIMAL`，原始结果使用 MySQL `JSON`。 |
| Vue 3.5.39 + Element Plus 2.14.3 | 兼容 | Element Plus 是 Vue 3 组件库；不混用 Element UI（Vue 2）。 |
| Vue 3 + Vite 8.1.4 | 兼容 | 使用官方 `@vitejs/plugin-vue` 6.0.8；开发和镜像构建统一使用 Node.js 22 LTS。 |
| Vite 8 + Node.js | 兼容但必须锁运行时 | 使用 Node 22 LTS，且不低于 Vite 官方要求的 22.12；使用 npm 管理依赖并提交生成后的 `package-lock.json`。 |
| Axios + Vue/Element Plus | 无直接版本冲突 | Axios 仅负责浏览器 HTTP 请求，统一创建一个实例并配置 90 秒超时。 |
| Docker Compose + Nginx + backend + MySQL | 兼容 | 前端构建产物放入 Nginx；Compose 仅保留 README 规定的三个服务，MySQL 健康后再启动后端。 |

### 2.1 Spring Boot 与 Rainforest 的强制隔离方案

1. `pom.xml` 中不得出现名称包含 `rainforest` 的第三方依赖。
2. 后端只允许访问配置项 `app.rainforest.base-url=https://api.rainforestapi.com`，不直接请求用户提交的 URL，避免 SSRF。
3. 用户链接只用于解析 `amazon_domain` 和 ASIN；实际请求固定为 `GET /request?type=product&amazon_domain=...&asin=...`。
4. Rainforest 响应先接收为 `JsonNode`，只归一化标题、品牌、品类、价格、币种、核心功能、规格和图片等稳定业务字段。
5. `Semaphore(1, true)` 作为单例 Bean；获取许可后再次检查缓存，调用结束或异常时必须在 `finally` 释放。
6. Caffeine 产品缓存建议 `maximumSize=100`、`expireAfterWrite=6h`；分析缓存建议 `maximumSize=200`、`expireAfterWrite=24h`。
7. 单实例排队超过 10 秒时返回明确的“商品服务繁忙”错误；Rainforest 网络读取超时设为 60 秒。
8. 禁止记录带 `api_key` 的完整请求 URL；日志只记录 ASIN、Amazon 域名、耗时、Rainforest 成功状态和内部请求 ID。
9. 当前方案只承诺一个 backend 容器。未来扩展到多个后端副本时，Java `Semaphore` 不再是全局锁，必须改用 Redis/数据库分布式限流；这不属于首版范围。

### 2.2 必须避免的依赖组合

后端只声明下列直接依赖：

```text
spring-boot-starter-web
spring-boot-starter-webflux
spring-boot-starter-validation
spring-boot-starter-cache
spring-boot-starter-actuator
com.github.ben-manes.caffeine:caffeine
com.baomidou:mybatis-plus-spring-boot3-starter:3.5.17
com.mysql:mysql-connector-j (runtime，版本由 Boot 管理)
spring-boot-configuration-processor (optional)
```

不得直接声明：

```text
mybatis-plus-boot-starter
mybatis-plus-spring-boot4-starter
mybatis-spring-boot-starter
mybatis-spring
jackson-databind 的手工版本
spring-framework-bom
Rainforest 第三方 SDK
DeepSeek/OpenAI Java SDK
任何测试 starter 或测试库
```

Spring WebFlux starter 的目的只是提供 WebClient。应用入口和配置中显式设为 Servlet，使 MyBatis/JDBC、Caffeine、`Semaphore` 和 Controller 都处于一致的阻塞式 MVC 模型，避免“部分响应式、部分阻塞式”造成线程和上下文混乱。

## 3. 外部 API 与样例数据处理策略

### 3.1 Rainforest

- 使用 Product Data API 的 `GET /request`，参数固定包含 `api_key`、`type=product`、`amazon_domain` 和 `asin`。
- 优先传 ASIN 而非整条商品 URL，使不同 URL 形式最终归一到同一缓存键 `amazon_domain:asin`。
- `demo.json` 表明价格位于 `product.buybox_winner.price`，字段包含 `value`、`currency`、`raw`；均可能缺失。
- 品类优先读取 `product.categories_flat`，缺失时由 `product.categories[*].name` 拼接。
- 核心功能读取 `product.feature_bullets`；规格读取 `product.specifications[*].name/value`。
- 主图读取 `product.main_image.link`，补充图片读取 `product.images[*].link`。
- `demo.json` 中 `videos_count=6` 但 `videos_additional` 实际有 10 项，说明计数字段不能被当作数组长度约束。
- 同一响应内价格既可能是字符串（例如保护计划）也可能是数字（例如 buy box）；归一化时只读取 buy box，并以文本转 `BigDecimal`，转换失败则保留 `null` 和原始 `raw`。
- 可选字段必须使用空值安全读取；不得为 46 个 `product` 字段建立刚性完整 DTO。

### 3.2 DeepSeek

- 基础地址使用 `https://api.deepseek.com`，接口使用 `/chat/completions`。
- 模型默认使用 `deepseek-v4-pro`；`deepseek-chat` 和 `deepseek-reasoner` 已公告于 2026-07-24 停用，不得写入新代码。
- 请求只传归一化后的商品事实，不发送无关视频链接、完整 HTML 或整个 Rainforest 原始 JSON，以降低 token、延迟和幻觉风险。
- 要求模型只返回 JSON：

```json
{
  "targetUsers": ["..."],
  "useCases": ["..."],
  "painPoints": ["..."],
  "coreSellingPoints": [
    {"claim": "...", "evidence": "来自商品事实的依据"}
  ],
  "videoScript": "150 字以内中文口播文案"
}
```

- System Prompt 必须禁止虚构价格、材质、认证、效果和优惠；卖点必须带商品事实依据。
- 后端进行确定性检查：必填数组非空、卖点含依据、口播非空且中文字符统计不超过 150。
- 首次输出不合法时，只允许基于校验错误再请求一次；第二次仍不合法则返回可理解的 AI 输出错误并保存原始响应用于排查。

## 4. 总体架构和请求流程

```text
Browser
  -> Nginx (/ 静态 Vue, /api 反向代理)
     -> Spring MVC Controller
        -> AmazonUrlParser（只解析，不访问用户 URL）
        -> AnalysisApplicationService
           -> Caffeine analysis cache
           -> MySQL 已有且未过期结果
           -> Caffeine product cache
           -> Semaphore(1) + Rainforest WebClient
           -> ProductNormalizer
           -> DeepSeek WebClient
           -> AI 输出校验
           -> MySQL 持久化
        <- 统一 API 响应
```

同步请求适合当前笔试题规模，也便于前端显示一个连续的加载状态。Nginx、Axios 和后端外部调用超时需统一：浏览器 90 秒、Nginx 120 秒、Rainforest 60 秒、DeepSeek 60 秒，确保错误最先由业务层转换，而不是由代理产生不明 504。

## 5. 数据库设计

### 5.1 `product_snapshot`

```sql
CREATE TABLE product_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    amazon_domain VARCHAR(64) NOT NULL,
    asin VARCHAR(16) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    title VARCHAR(1024) NOT NULL,
    brand VARCHAR(255) NULL,
    category_path VARCHAR(1024) NULL,
    price_amount DECIMAL(19, 4) NULL,
    currency VARCHAR(8) NULL,
    main_image_url VARCHAR(2048) NULL,
    normalized_json JSON NOT NULL,
    raw_json JSON NOT NULL,
    fetched_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_domain_asin (amazon_domain, asin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 5.2 `analysis_result`

```sql
CREATE TABLE analysis_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_snapshot_id BIGINT UNSIGNED NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    target_users JSON NOT NULL,
    use_cases JSON NOT NULL,
    pain_points JSON NOT NULL,
    core_selling_points JSON NOT NULL,
    video_script VARCHAR(600) NOT NULL,
    ai_raw_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_analysis_product_model_prompt
        (product_snapshot_id, model, prompt_version),
    CONSTRAINT fk_analysis_product
        FOREIGN KEY (product_snapshot_id) REFERENCES product_snapshot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

实体中的 JSON 字段保存为 `String`，写入前由 Jackson 序列化并确认是合法 JSON；不要在首版引入复杂自定义 TypeHandler，以降低 MyBatis-Plus、Jackson 和 MySQL 之间的耦合。

## 6. HTTP API 契约

### 6.1 创建或读取分析

`POST /api/v1/product-analyses`

请求：

```json
{
  "amazonUrl": "https://www.amazon.com/dp/B073JYC4XM"
}
```

成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "source": "LIVE",
    "product": {
      "amazonDomain": "amazon.com",
      "asin": "B073JYC4XM",
      "title": "...",
      "brand": "...",
      "categoryPath": "...",
      "price": {"amount": 35.95, "currency": "USD", "display": "$35.95"},
      "mainImageUrl": "...",
      "features": ["..."],
      "specifications": [{"name": "...", "value": "..."}]
    },
    "analysis": {
      "targetUsers": ["..."],
      "useCases": ["..."],
      "painPoints": ["..."],
      "coreSellingPoints": [{"claim": "...", "evidence": "..."}],
      "videoScript": "..."
    }
  },
  "requestId": "..."
}
```

`source` 取值为 `CACHE`、`DATABASE` 或 `LIVE`，便于演示缓存价值但不暴露内部密钥或原始 AI 响应。

### 6.2 按记录读取

`GET /api/v1/product-analyses/{id}`

用于刷新页面后恢复结果。只返回归一化商品信息和分析，不返回 Rainforest/DeepSeek 原始 JSON。

### 6.3 错误码

| HTTP | 业务码 | 场景 |
|---|---|---|
| 400 | `INVALID_AMAZON_URL` | 非 HTTPS、非允许的 Amazon 域名、无法解析 ASIN。 |
| 404 | `ANALYSIS_NOT_FOUND` | 查询的分析记录不存在。 |
| 409 | `RAINFOREST_BUSY` | 10 秒内未取得单请求许可。 |
| 422 | `PRODUCT_DATA_INCOMPLETE` | Rainforest 成功但缺少标题等最低商品事实。 |
| 429 | `UPSTREAM_QUOTA_EXCEEDED` | 外部 API 额度或限流。 |
| 502 | `RAINFOREST_ERROR` / `DEEPSEEK_ERROR` | 外部服务错误或无效响应。 |
| 504 | `UPSTREAM_TIMEOUT` | 外部调用超时。 |
| 500 | `INTERNAL_ERROR` | 未归类异常，响应中只返回 requestId。 |

## 7. 代码开发任务

### Task 1: 建立仓库骨架与版本锁定

**Files:**

- Create: `.gitignore`
- Create: `.env.example`
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/example/productassistant/ProductAssistantApplication.java`
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`（首次执行 `npm install` 时生成）
- Modify: `readme.md`

**Steps:**

1. 创建 `backend`、`frontend`、`deploy/nginx` 和 `database` 目录。
2. 以 Spring Boot 3.5.16 parent 和 Java 21 建立 Maven 项目，只加入第 2.2 节允许的后端依赖。
3. `pom.xml` 只显式锁 MyBatis-Plus 3.5.17；其余 Spring/Jackson/Caffeine/MySQL 版本交给 Boot 管理。
4. 建立 Vue JavaScript 项目，锁定 Vue 3.5.39、Vite 8.1.4、`@vitejs/plugin-vue` 6.0.8、Element Plus 2.14.3 和 Axios 1.13.x，使用 npm 11 管理依赖。
5. 在 `package.json` 增加 `engines.node >=22.12 <23` 和 npm `packageManager`，首次安装依赖后提交 `package-lock.json`。
6. `.env.example` 只放变量名与安全占位值，不提交真实密钥。
7. 更新 README 的版本表、目录结构和“禁止手工覆盖依赖版本”说明。
8. 编译后端并构建前端，确认基础工程可打包且没有测试源码。
9. Commit: `chore: scaffold version-locked application`

### Task 2: 建立后端配置、HTTP 客户端和缓存基础设施

**Files:**

- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/example/productassistant/config/AppProperties.java`
- Create: `backend/src/main/java/com/example/productassistant/config/HttpClientConfig.java`
- Create: `backend/src/main/java/com/example/productassistant/config/CacheConfig.java`
- Create: `backend/src/main/java/com/example/productassistant/config/RainforestConcurrencyConfig.java`

**Steps:**

1. 在 `application.yml` 设置 `spring.main.web-application-type=servlet`、数据库连接、MyBatis-Plus、Actuator 和 JSON 时区。
2. 用 `@ConfigurationProperties(prefix="app")` 映射 Rainforest、DeepSeek、缓存 TTL、排队和读取超时。
3. 建立两个命名 WebClient：Rainforest client 只允许 Rainforest base URL，DeepSeek client 自动添加 Bearer token。
4. 为 Reactor Netty 配置连接、响应、读取和写入超时，不在业务代码中散落超时数字。
5. 建立 `rainforestProducts` 与 `analyses` 两个 Caffeine cache，并按第 2.1 节设置大小和 TTL。
6. 注册公平的单例 `new Semaphore(1, true)`，Bean 名称明确为 `rainforestSemaphore`。
7. 为配置类加启动时校验：生产配置缺少 API key 时直接拒绝启动，但日志不打印 key。
8. Commit: `feat: add external client cache and concurrency configuration`

### Task 3: 建立数据库结构与持久化层

**Files:**

- Create: `database/init.sql`
- Create: `backend/src/main/java/com/example/productassistant/product/ProductSnapshotEntity.java`
- Create: `backend/src/main/java/com/example/productassistant/product/ProductSnapshotMapper.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/AnalysisResultEntity.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/AnalysisResultMapper.java`
- Create: `backend/src/main/java/com/example/productassistant/persistence/JsonStringCodec.java`

**Steps:**

1. 按第 5 节写入两个表、唯一键、外键和 `utf8mb4` 设置。
2. 实体使用 `Long`、`BigDecimal`、`LocalDateTime` 和 `String` JSON，不使用数据库关键字作字段名。
3. Mapper 只继承 `BaseMapper`；首版不增加 XML SQL。
4. `JsonStringCodec` 负责将对象与数组转换成合法 JSON 字符串，并拒绝空或损坏 JSON。
5. 实现按 `amazon_domain + asin` 查询/更新商品，以及按 `product + model + promptVersion` 查询/更新分析的方法。
6. 唯一键冲突采用“查询后更新”处理，不把数据库异常直接返回前端。
7. Commit: `feat: add product and analysis persistence`

### Task 4: 实现 Amazon 链接校验和 ASIN 归一化

**Files:**

- Create: `backend/src/main/java/com/example/productassistant/amazon/AmazonProductKey.java`
- Create: `backend/src/main/java/com/example/productassistant/amazon/AmazonUrlParser.java`
- Create: `backend/src/main/java/com/example/productassistant/api/CreateAnalysisRequest.java`

**Steps:**

1. 请求 DTO 使用 Bean Validation，限制 URL 非空且最大 2048 字符。
2. 只接受 `https`，拒绝用户名密码、非标准端口、IP 地址、localhost 和非 Amazon 主机。
3. 主机白名单支持公开 Amazon 市场域名，如 `amazon.com`、`amazon.co.uk`、`amazon.de`、`amazon.co.jp`，并用明确集合维护。
4. 从 `/dp/{ASIN}`、`/gp/product/{ASIN}`、`/product/{ASIN}` 和标题化 `/.../dp/{ASIN}` 中解析 10 位字母数字 ASIN。
5. 将主机转小写、去除 `www.`，ASIN 转大写，得到唯一 `AmazonProductKey`。
6. 不跟随用户 URL 重定向、不抓取 Amazon 页面、不把原 URL 作为 WebClient 目标。
7. Commit: `feat: parse and validate amazon product urls`

### Task 5: 实现 Rainforest 防冲突客户端与商品归一化

**Files:**

- Create: `backend/src/main/java/com/example/productassistant/rainforest/RainforestClient.java`
- Create: `backend/src/main/java/com/example/productassistant/rainforest/RainforestResponse.java`
- Create: `backend/src/main/java/com/example/productassistant/rainforest/RainforestException.java`
- Create: `backend/src/main/java/com/example/productassistant/product/NormalizedProduct.java`
- Create: `backend/src/main/java/com/example/productassistant/product/ProductNormalizer.java`
- Create: `backend/src/main/java/com/example/productassistant/product/ProductSnapshotService.java`

**Steps:**

1. `RainforestClient` 只组装官方 `GET /request` 参数，响应主体读取为 `JsonNode`。
2. 检查 HTTP 状态、`request_info.success` 和常见错误字段，将额度、限流、无商品、超时分别映射为内部异常类型。
3. `ProductNormalizer` 依据第 3.1 节提取稳定字段；所有可选字段为空时都能继续，唯独标题和 ASIN 为最低必需事实。
4. 对 feature bullets 和 specifications 设置数量与单项长度上限，避免把超大上游内容发送给 DeepSeek。
5. `ProductSnapshotService` 依次检查 Caffeine、六小时内的 MySQL 快照，再尝试获取 Semaphore。
6. 获取 Semaphore 后再次检查缓存，防止等待期间已有请求完成；调用结束在 `finally` 释放许可。
7. 保存归一化 JSON 与完整原始 JSON，并使用 upsert 语义刷新同一域名和 ASIN 的记录。
8. 日志使用 requestId、ASIN、域名、耗时和 credits，不记录完整上游 URL、API key 或原始 JSON。
9. Commit: `feat: integrate rainforest with guarded product normalization`

### Task 6: 实现 DeepSeek 分析与内容质量约束

**Files:**

- Create: `backend/src/main/resources/prompts/product-analysis-v1.txt`
- Create: `backend/src/main/java/com/example/productassistant/deepseek/DeepSeekClient.java`
- Create: `backend/src/main/java/com/example/productassistant/deepseek/DeepSeekRequest.java`
- Create: `backend/src/main/java/com/example/productassistant/deepseek/DeepSeekResponse.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/ProductAnalysis.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/AnalysisOutputValidator.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/AnalysisGenerationService.java`

**Steps:**

1. Prompt 文件使用版本号 `v1`，明确中文、结构、事实依据、150 字和开头钩子要求。
2. 只发送归一化商品事实，并限制总输入长度；规格按重要性截取，避免原始 JSON 全量进入模型。
3. 调用 `deepseek-v4-pro` 的非流式 chat completions，并要求 JSON object 输出。
4. 去除可能的 Markdown fence 后用 Jackson 解析；字段缺失或类型错误时返回结构化校验错误。
5. `AnalysisOutputValidator` 统计 Unicode code point，确保 `videoScript` 不超过 150 个字符；同时校验每个核心卖点都有 evidence。
6. 首次不合法时以错误列表发起一次修复请求；严禁无限重试。
7. 保存模型名、prompt 版本、结构化分析 JSON 与完整 DeepSeek 原始响应。
8. Commit: `feat: generate grounded product analysis with deepseek`

### Task 7: 实现应用编排、缓存和事务边界

**Files:**

- Create: `backend/src/main/java/com/example/productassistant/analysis/AnalysisApplicationService.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/AnalysisCacheKey.java`
- Create: `backend/src/main/java/com/example/productassistant/api/ProductAnalysisView.java`
- Create: `backend/src/main/java/com/example/productassistant/api/ApiResponse.java`

**Steps:**

1. 编排流程固定为：解析链接 -> 分析缓存 -> 数据库有效分析 -> 商品快照 -> DeepSeek -> 持久化 -> 缓存 -> 响应。
2. 分析缓存键至少包含 Amazon 域名、ASIN、模型名和 prompt 版本，防止 prompt 升级后误用旧内容。
3. `source` 明确标识 `CACHE`、`DATABASE` 或 `LIVE`。
4. Rainforest 和 DeepSeek 网络调用不得放在数据库事务中；只对短暂的最终写入开启事务。
5. 对同一商品的并发 DeepSeek 生成可用进程内 key lock 合并，但不得复用 Rainforest 的全局 Semaphore。
6. API View 与数据库 Entity 分离，不暴露 raw JSON、错误堆栈、密钥或内部配置。
7. Commit: `feat: orchestrate cached product analysis workflow`

### Task 8: 实现 Controller、错误响应和可观测性

**Files:**

- Create: `backend/src/main/java/com/example/productassistant/api/ProductAnalysisController.java`
- Create: `backend/src/main/java/com/example/productassistant/api/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/example/productassistant/api/ApiErrorCode.java`
- Create: `backend/src/main/java/com/example/productassistant/observability/RequestIdFilter.java`

**Steps:**

1. 实现第 6 节的 POST 和 GET 接口，Controller 仅负责参数、状态码和调用应用服务。
2. 用统一异常处理器映射第 6.3 节错误码，未知错误只返回 requestId。
3. 从请求头接收或生成 `X-Request-Id`，写入 MDC 并在响应头返回。
4. Actuator 只公开 `health` 和 `info`；不公开 env、beans、configprops 等敏感端点。
5. 配置日志对 `api_key`、`Authorization` 和环境变量值脱敏。
6. Commit: `feat: expose product analysis api with safe errors`

### Task 9: 建立前端 API 层与单页状态

**Files:**

- Create: `frontend/index.html`
- Create: `frontend/vite.config.js`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/api/http.js`
- Create: `frontend/src/api/productAnalysis.js`
- Create: `frontend/src/model/productAnalysis.js`
- Create: `frontend/src/composables/useProductAnalysis.js`

**Steps:**

1. 建立 Axios 实例，base URL 为 `/api`，超时 90 秒；响应拦截器统一提取业务错误信息和 requestId。
2. `useProductAnalysis` 管理 `idle/loading/success/error` 四态，提交期间禁用按钮并阻止重复请求。
3. 保存最近一次成功结果到内存；不把 API key、原始上游响应或敏感数据放入浏览器存储。
4. 开发环境由 Vite 代理 `/api` 到本地后端，生产环境由 Nginx 代理。
5. Commit: `feat: add frontend product analysis state and api client`

### Task 10: 实现产品分析界面

**Files:**

- Create: `frontend/src/components/AnalysisForm.vue`
- Create: `frontend/src/components/LoadingSteps.vue`
- Create: `frontend/src/components/ProductSummary.vue`
- Create: `frontend/src/components/AnalysisSections.vue`
- Create: `frontend/src/components/VideoScriptCard.vue`
- Create: `frontend/src/components/ErrorPanel.vue`
- Create: `frontend/src/styles/theme.css`
- Modify: `frontend/src/App.vue`

**Steps:**

1. 页面顶部展示一句用途说明、Amazon URL 输入框、示例占位符和主要操作按钮。
2. 加载时展示“解析链接 / 获取商品 / 生成分析”三个非伪造进度提示，不承诺精确百分比。
3. 商品摘要展示图片、标题、品牌、品类、价格、核心功能和规格；缺失值使用“暂无”而不是空白或虚构内容。
4. 产品分析按目标用户、使用场景、用户痛点和核心卖点分区，卖点旁显示依据。
5. 口播卡片突出 150 字限制并提供浏览器剪贴板复制按钮。
6. 错误面板显示可行动提示和 requestId；额度、超时、繁忙、无效链接使用不同文案。
7. 用 CSS 保证桌面和手机布局可读，Element Plus 只用于表单、按钮、卡片、提示和 skeleton，避免无必要组件。
8. Commit: `feat: build responsive product analysis interface`

### Task 11: 容器化并接入 Nginx

**Files:**

- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `deploy/nginx/default.conf`
- Create: `compose.yaml`
- Create: `.dockerignore`

**Steps:**

1. 后端先在本地打包，再由 Java 21 JRE 镜像直接复制 `backend/target/product-assistant-0.0.1-SNAPSHOT.jar`，以非 root 用户运行；容器内不复制或编译源码。
2. 前端使用本地 npm 生成 `frontend/dist`，Nginx stable-alpine 镜像直接复制该目录；容器内不复制或编译源码。
3. Nginx 对 `/api/` 反向代理到 `backend:8080`，配置必要转发头和 120 秒读取超时；其他路径回退 `index.html`。
4. Compose 只定义 `nginx`、`backend`、`mysql` 三个 README 规定服务。
5. MySQL 使用 8.4 LTS、命名 volume、`utf8mb4` 和 healthcheck；backend 依赖 `service_healthy`。
6. backend healthcheck 使用 `/actuator/health`；Nginx 仅在 backend 健康后对外提供服务。
7. API key、数据库密码和模型名来自环境变量；Compose 文件不得包含真实密钥。
8. Commit: `feat: containerize nginx backend and mysql stack`

### Task 12: 完善交付文档与人工验收清单

**Files:**

- Modify: `readme.md`
- Modify: `.env.example`
- Create: `docs/architecture.md`
- Create: `docs/api.md`

**Steps:**

1. README 写明前置版本、环境变量、开发启动、Docker Compose 启动、端口和常见错误。
2. 架构文档解释为何 Rainforest 不会与 Spring Boot 发生依赖冲突，以及 `Semaphore(1)` 的单实例边界。
3. API 文档写入第 6 节契约、错误码和不返回 raw JSON 的安全约束。
4. 用 `mvn -DskipTests package`、`npm run build` 和 `docker compose config` 确认构建配置有效。
5. 启动 Compose，人工检查三个服务健康、首页可打开、API 可通过 Nginx 访问、数据库数据可在重启后保留。
6. 人工使用至少三个不同公开 Amazon 域名或 URL 形态，确认不存在针对 `demo.json` 商品的硬编码。
7. 人工确认口播字符数、钩子、事实依据、错误提示、API key 脱敏和缓存来源标识。
8. 确认仓库中不存在任何测试源码、测试目录或测试依赖。
9. Commit: `docs: finalize deployment and operating guide`

## 8. 环境变量

```dotenv
RAINFOREST_API_KEY=replace_me
RAINFOREST_BASE_URL=https://api.rainforestapi.com
DEEPSEEK_API_KEY=replace_me
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-pro
MYSQL_DATABASE=product_assistant
MYSQL_USER=product_app
MYSQL_PASSWORD=replace_me
MYSQL_ROOT_PASSWORD=replace_me
SPRING_PROFILES_ACTIVE=prod
```

真实部署时应由部署平台注入密钥；`.env` 必须被 Git 忽略。前端构建参数中不得出现 Rainforest 或 DeepSeek API key。

## 9. 完成标准

- 任意受支持的公开 Amazon 商品 URL 能被解析为域名和 ASIN，后端不直接抓取用户 URL。
- Rainforest 在单 backend 实例内任何时刻最多只有一个在途调用，异常和超时后许可不会泄漏。
- `pom.xml` 不含 Rainforest/DeepSeek SDK、原生 MyBatis starter 或手工 Jackson 版本。
- Rainforest 字段缺失、类型变化或额外字段不会导致整个 JSON 反序列化失败。
- 产品信息、Rainforest 原始 JSON、结构化 AI 分析和 DeepSeek 原始响应均正确入库。
- 页面展示产品名称、品类、价格、核心功能、规格、分析维度和口播文案。
- 口播最多 150 字，开头有可识别钩子，核心卖点包含事实依据。
- 重复商品请求能命中 Caffeine 或 MySQL，避免重复消耗 Rainforest credits。
- Nginx、backend、mysql 三个 Compose 服务可以稳定启动，MySQL 就绪后 backend 才启动。
- 日志、API 响应、Git 文件和浏览器资源中均没有真实 API key。
- 仓库不存在任何测试代码、测试目录或测试依赖。

## 10. 主要风险与处置

| 风险 | 处置 |
|---|---|
| Rainforest 响应字段随 Amazon 页面变化 | `JsonNode` + 小型归一化 DTO + 原始 JSON 留存 + 空值安全。 |
| Rainforest credits 被重复请求消耗 | 归一化缓存键、双重缓存检查、数据库 TTL、`Semaphore(1)`。 |
| 外部 API 调用时间较长 | 分层超时、明确加载态、统一错误码；首版保持同步以控制复杂度。 |
| AI 产生夸大或无依据卖点 | 只传事实、卖点 evidence、后端确定性校验、最多一次修复请求。 |
| DeepSeek 旧模型名停用 | 默认锁定 `deepseek-v4-pro`，模型通过环境变量可替换。 |
| Spring MVC 与 WebFlux 混用 | 显式 Servlet 模式，WebClient 仅出站，不编写 reactive Controller。 |
| MyBatis-Plus 拉入不一致依赖 | 只使用 Boot 3 starter，不重复引入 MyBatis；依赖版本以 Boot BOM 为主。 |
| MySQL JSON 和 Java 对象耦合 | 实体以合法 JSON 字符串存储，业务对象由 Jackson 显式编解码。 |
| 多副本后 Semaphore 失效 | 首版固定单 backend；扩容前改用分布式限流。 |

## 11. 官方参考资料

- [Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Framework：Spring MVC 与 WebClient 可并存](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Boot 调用 REST 服务](https://docs.spring.io/spring-boot/3.5/reference/io/rest-client.html)
- [MyBatis-Plus 安装与 Spring Boot 3 starter](https://baomidou.com/en/getting-started/install/)
- [Rainforest Product Data API 入门](https://docs.trajectdata.com/rainforestapi/product-data-api/overview)
- [Rainforest 通用请求参数](https://docs.trajectdata.com/rainforestapi/product-data-api/parameters/common)
- [DeepSeek API 快速开始与当前模型名](https://api-docs.deepseek.com/)
- [Vite 官方入门与 Node.js 要求](https://vite.dev/guide/)
- [Element Plus 安装与浏览器兼容性](https://element-plus.org/en-US/guide/installation.html)
- [Docker Compose 服务启动顺序与 healthcheck](https://docs.docker.com/compose/how-tos/startup-order/)
