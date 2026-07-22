# 系统架构

## 运行边界

```text
浏览器
  └─ HTTPS / TLS 1.2 或 1.3
      └─ Nginx :443（证书、HSTS、安全响应头、SPA 静态资源）
          └─ Docker 私有 bridge 网络
              └─ Spring Boot :8080（不映射宿主机端口）
                  ├─ Spring MVC + Spring Security
                  ├─ MyBatis-Plus + Flyway -> MySQL 8.4
                  └─ Spring WebClient -> Rainforest / DeepSeek HTTPS API
```

生产环境的 80 端口只执行 HTTPS 301 跳转。Nginx 是唯一公网入口与 TLS 终止点；后端信任同一主机私有 Docker 网络中的代理，并通过 `X-Forwarded-Proto` 识别原始 HTTPS。Nginx 用连接的 `$remote_addr` 重写 `X-Forwarded-For`，同时清除客户端提交的 `Forwarded`，不接受伪造的转发链，供认证 IP 限流使用；若以后接入 CDN，必须先配置可信代理网段和 real-ip 规则。API location 强制 `Cache-Control: no-store`，并重复声明 HSTS、CSP、nosniff、frame protection 等安全头，避免 Nginx `add_header` 的 location 覆盖规则丢失父级响应头。

## 认证、Session 与 CSRF

认证使用 Spring Security 服务端 Session，而不是浏览器持久化 Token：

1. 浏览器先匿名调用 `GET /api/v1/auth/session`，服务端生成 `XSRF-TOKEN` Cookie。
2. 注册或登录成功后轮换 Session ID、删除登录前 CSRF Cookie，并把已清除密码哈希的 `SecurityContext` 保存到服务端 Session；前端随后查询 session 获取新 CSRF Token。
3. Session Cookie 设置 HttpOnly、SameSite=Lax；生产环境必须同时设置 Secure，因此 JavaScript 无法读取 Session ID。
4. SPA 只读取 CSRF Cookie，并在 POST 请求中使用 `X-XSRF-TOKEN` 回传。缺失或无效 Token 返回 403。
5. 退出销毁 Session、清除认证上下文和旧 CSRF Token；前端随后重新查询 session。

邮箱在入库与认证前统一 trim、Unicode 规范化并按 `Locale.ROOT` 转小写。密码限制为 12～72 UTF-8 bytes，使用 BCrypt cost 12 单向哈希；密码不使用可逆加密。登录和注册同时使用来源 IP 独立桶与“来源 IP + 规范化邮箱”组合桶做进程内速率限制；注册成功不会清空 IP 独立桶，避免轮换邮箱批量领取赠送积分。登录失败使用统一错误，防止账户枚举。

除匿名 session、注册和登录端点外，所有 `/api/**` 都要求认证。活动用户过滤器会在每个已认证 API 请求重新读取 `app_user.enabled`；账号被禁用或删除后立即销毁旧 Session 并返回 401，而不是继续信任最长 8 小时的登录快照。Actuator 不通过 Nginx 对外代理。

## 分析与积分数据流

```text
POST + userId + X-Idempotency-Key(UUID)
  -> 短事务：原子扣减 1 分并创建 RESERVED 账本
  -> URL 解析 / 缓存 / MySQL / Rainforest / DeepSeek / 校验 / AES-GCM 保存
  -> 短事务：RESERVED -> SETTLED，同时写 user_analysis_access
  -> 返回 result + remainingPoints

任一业务步骤失败
  -> 独立短事务：RESERVED -> REFUNDED，原子加回 1 分
  -> 原异常交给全局错误处理
```

外部网络调用期间不持有数据库事务。积分状态机为：

```text
新请求 ──预占成功──> RESERVED ──分析成功──> SETTLED
                          ├─明确失败──────> REFUNDED
                          └─超过15分钟────> REFUNDED（定时恢复）
```

`UPDATE app_user SET points = points - 1 WHERE id = ? AND enabled = 1 AND points > 0` 保证并发下余额不会小于 0。`user_id + request_id` 唯一键保证同一用户的同一幂等键只产生一笔预占：重复 `SETTLED` 返回已有结果；重复 `RESERVED` 返回正在处理；重复 `REFUNDED` 返回已失败。退款使用状态条件更新，只有一次 `RESERVED -> REFUNDED` 能加回余额。

新用户注册在一个事务中创建 `points=10` 的用户和 `REGISTER_BONUS / SETTLED / delta=10` 账本。每次成功 POST 都扣 1 分，包括 `LIVE`、`LOCAL_DEMO`、`DATABASE` 与 `CACHE`；是否消耗外部 API 不是扣费边界。

共享分析结果与用户权限分离：Caffeine 和 `analysis_result` 不包含 userId、积分或 Session。结算事务通过 `user_analysis_access` 授予当前用户访问权。GET 按 ID 查询先检查访问表；无记录时与 ID 不存在统一返回 404，避免枚举其他用户的结果。

## 商品与 AI 数据流

```text
AnalysisApplicationService
  -> Caffeine 分析缓存
  -> MySQL 已有分析
  -> ProductSnapshotService
     -> Caffeine 商品缓存
     -> MySQL 新鲜商品快照
     -> Semaphore(1)
     -> Rainforest WebClient
     -> ProductNormalizer
     -> MySQL 商品快照
  -> DeepSeek WebClient
  -> AnalysisOutputValidator
  -> AES-256-GCM 加密口播
  -> MySQL 分析结果
```

### 为什么 Rainforest 不会与 Spring Boot 冲突

Rainforest 通过标准 HTTPS REST API 提供服务。后端 `pom.xml` 不包含 Rainforest SDK，Rainforest 不会把 Jackson、Reactor、Servlet 或 MyBatis 依赖带入 JVM，因此不存在覆盖 Spring Boot 传递依赖的可能。集成边界只有 JSON HTTP 契约：

1. `RainforestClient` 使用 Spring Boot 管理的 WebClient，目标固定为配置的 Rainforest base URL 和 `/request`。
2. 用户 URL 不会成为 HTTP 客户端目标，只解析白名单 Amazon 域名和 ASIN。
3. 完整响应先接收为 Jackson `JsonNode`，再由 `ProductNormalizer` 提取稳定业务字段。
4. 原始 JSON 与归一化 JSON 同时保存在 MySQL，以便上游字段变化后排查。

### Spring MVC 与 WebClient

入站请求采用 Spring MVC/Servlet 模式，因为 Spring Security Session、MyBatis-Plus、JDBC 和信号量都是阻塞式组件。`spring.main.web-application-type=servlet` 明确锁定该行为。

`spring-boot-starter-webflux` 只为出站 WebClient 提供 Reactor Netty。Controller 不返回 `Mono` 或 `Flux`，网络调用在应用服务中同步完成，避免把阻塞数据库操作放入响应式事件循环。

### Rainforest 并发和缓存

`rainforestSemaphore` 是公平的单例 `Semaphore(1, true)`：

- 调用前先检查 Caffeine 和六小时内的 MySQL 快照。
- 等待许可最多 10 秒，超时返回 `RAINFOREST_BUSY`。
- 获得许可后再次检查缓存，合并等待期间已完成的相同请求。
- 外部调用位于 `try/finally` 中，任何错误都会释放许可。
- API key 不写入业务日志。

该约束只在一个 JVM 内有效，所以 Compose 固定一个 backend 服务。多副本部署前必须使用 Redis、数据库或网关实现全局限流。

精确 URL `https://www.amazon.com/dp/B073JYC4XM?th=1&psc=1` 由 `DemoRainforestResponseProvider` 从 classpath 的 `demo.json` 提供商品数据，不获取 Rainforest Semaphore 或 credits，但仍实时调用 DeepSeek；积分规则与其他成功来源相同。

### DeepSeek 内容约束

- 默认模型由环境变量配置。
- 只向模型发送归一化商品事实，不发送 Rainforest 全量响应。
- 要求 JSON object 输出，卖点必须含 `claim` 和 `evidence`。
- 后端按 Unicode code point 统计口播长度，限制为 150 字符。
- 首次结果无效时只允许一次修复请求，防止无限重试和费用失控。
- 持久化层只保留白名单 AI 审计元数据，不保存 `choices.message.content`，避免口播在 `ai_raw_json` 中形成第二份明文。

## AES-256-GCM 口播加密

只有口播文案使用对称加密；用户密码始终使用 BCrypt 单向哈希。每条分析记录的口播都使用 `AES/GCM/NoPadding`、独立的 `SecureRandom` 12-byte IV 和 128-bit Tag。数据库保存密文、IV 和密钥版本，不保存密钥。

AAD 固定为：

```text
analysis-result:{productSnapshotId}:{model}:{promptVersion}:{keyVersion}
```

因此密文不能在不同商品、模型、Prompt 或密钥版本之间无声搬移。解密按记录中的 keyVersion 选择历史密钥；未知版本、AAD 不匹配或 GCM Tag 校验失败统一映射为安全内部错误，不向客户端泄露密码学细节。日志禁止输出口播明文、密钥、完整 IV 或完整密文。

`VIDEO_SCRIPT_ENCRYPTION_KEYS` 保存逗号分隔的版本化 Base64 密钥映射，解码后必须恰好 32 bytes；活动版本由 `VIDEO_SCRIPT_ACTIVE_KEY_VERSION` 指定。轮换时先加入新密钥并保留历史密钥，再切换活动版本。

## Flyway 与两阶段迁移

Flyway 是唯一数据库结构维护入口：

1. `V1__baseline_schema.sql` 固化原有 `product_snapshot` 与 `analysis_result`。空数据库依次执行 V1、V2；已有非空数据库 baseline 为 V1 后执行 V2。
2. `V2__user_points_and_video_script_encryption.sql` 增加用户、积分账本、访问权和加密列，把旧 `video_script` 暂时改为可空。
3. 第一阶段应用执行“双读、单写”：新数据只写密文；旧记录仍可读明文。显式开启回填开关后，分页、短事务地加密旧文案、清空对应明文，并把旧 `ai_raw_json` 改写为不含生成内容的迁移审计标记。
4. 只有“缺少加密字段的行数”和“仍含明文的行数”都为 0，并完成第二次备份后，才在第二阶段发布中创建 V3，将加密列改为非空并删除旧明文列。

当前第一阶段 JAR 故意不包含 V3。禁止绕过 Flyway 手工执行 ALTER，否则实际结构会与 `flyway_schema_history` 不一致。

## 镜像与 Secret 边界

容器镜像不负责编译源码：后端镜像只复制本地 JAR，前端镜像只复制本地 `dist/`。Nginx 证书与私钥、后端 API key、AES 密钥和数据库密码都在运行时注入或只读挂载，不进入 JAR、静态资源、镜像或版本库。生产 profile 还要求 Rainforest 与 DeepSeek Base URL 使用 HTTPS，防止 API 凭据和请求内容明文出站。

安全日志允许记录 requestId、userId、分析 ID、账本 ID、错误分类和非敏感 keyVersion；禁止记录完整邮箱、密码、Session ID、CSRF Token、外部 API key、AES 密钥和口播明文。
