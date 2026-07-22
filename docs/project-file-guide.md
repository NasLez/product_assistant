# 项目文件职责说明

本文档按当前 `.gitignore` 规则列出项目文件及职责；不包含 `.env`、构建产物、依赖目录、证书私钥等被忽略内容，也不列出 Git 自身的 `.git/` 元数据目录。

```text
new_project/
├─ .dockerignore
│  └─ 约束根目录 Docker 构建上下文，排除密钥、后端产物、依赖和无关文档，并保留前端 dist 供镜像直接复制。
├─ .env.example
│  └─ 生产部署环境变量模板，包含数据库、外部 API、TLS、Session、加密、认证限流和积分恢复配置占位值。
├─ .gitignore
│  └─ 排除本地密钥、TLS 证书、IDE 文件、依赖、构建产物、日志和本地数据。
├─ 1.md
│  └─ 产品分析维度与短视频内容角度的原始业务材料。
├─ 2.md
│  └─ HTTPS、口播加密、邮箱认证和积分功能的新需求原文。
├─ README.md
│  └─ 项目总说明，覆盖技术栈、开发部署、认证积分、AES 加密、Flyway 迁移、TLS 和安全边界。
├─ compose.yaml
│  └─ 编排 MySQL、Spring Boot 与 Nginx，注入后端 Secret，挂载 TLS 模板和证书，并配置网络与健康检查。
│
├─ backend/
│  ├─ Dockerfile
│  │  └─ 基于 Java 21 JRE 运行本地已打包 JAR，并以非 root 用户启动后端。
│  ├─ Dockerfile.dockerignore
│  │  └─ 后端镜像专用白名单，只让 Dockerfile 和目标 JAR 进入构建上下文。
│  ├─ pom.xml
│  │  └─ Maven 配置，统一管理 Spring Boot、Security、Flyway、WebClient、MyBatis-Plus、MySQL 和 Caffeine 等依赖。
│  └─ src/main/
│     ├─ java/
│     │  ├─ demo.json
│     │  │  └─ 指定 Amazon 演示链接对应的 Rainforest 样例响应，避免消耗 Rainforest 免费次数。
│     │  └─ com/example/productassistant/
│     │     ├─ ProductAssistantApplication.java
│     │     │  └─ Spring Boot 入口，启用配置属性、Mapper 扫描和定时任务。
│     │     │
│     │     ├─ amazon/
│     │     │  ├─ AmazonProductKey.java
│     │     │  │  └─ 保存标准化 Amazon 域名、ASIN、原始 URL 和商品缓存键。
│     │     │  ├─ AmazonUrlParser.java
│     │     │  │  └─ 白名单校验公开 HTTPS Amazon 商品链接并提取 ASIN。
│     │     │  └─ InvalidAmazonUrlException.java
│     │     │     └─ 表示商品链接格式、站点、端口或 ASIN 不合法。
│     │     │
│     │     ├─ analysis/
│     │     │  ├─ AnalysisApplicationService.java
│     │     │  │  └─ 编排商品取数、缓存/数据库复用、AI 生成、口播加解密和分析持久化。
│     │     │  ├─ AnalysisCacheKey.java
│     │     │  │  └─ 由商品、模型和 Prompt 版本组成不含用户数据的分析缓存键。
│     │     │  ├─ AnalysisGenerationService.java
│     │     │  │  └─ 调用 DeepSeek、解析结构化输出，并在首次无效时执行一次修复请求。
│     │     │  ├─ AnalysisNotFoundException.java
│     │     │  │  └─ 表示分析不存在或当前用户无权读取。
│     │     │  ├─ AnalysisOutputValidator.java
│     │     │  │  └─ 校验分析数组、卖点证据和 150 字口播限制。
│     │     │  ├─ AnalysisResultEntity.java
│     │     │  │  └─ 映射 analysis_result，包括结构化 JSON、口播密文、IV、密钥版本及迁移期旧列。
│     │     │  ├─ AnalysisResultMapper.java
│     │     │  │  └─ 访问分析表，支持唯一结果查询和旧明文分页回填。
│     │     │  ├─ AnalysisSubmissionService.java
│     │     │  │  └─ 串联积分预占、分析执行、结算授权和失败退款。
│     │     │  ├─ AnalysisSubmissionView.java
│     │     │  │  └─ 封装分析结果与成功请求后的剩余积分。
│     │     │  ├─ GeneratedAnalysis.java
│     │     │  │  └─ 封装结构化 AI 分析与上游响应，供持久化层提取白名单审计元数据。
│     │     │  ├─ InvalidIdempotencyKeyException.java
│     │     │  │  └─ 表示分析请求缺少合法 UUID 幂等键。
│     │     │  ├─ ProductAnalysis.java
│     │     │  │  └─ 定义目标用户、场景、痛点、卖点和口播文案领域模型。
│     │     │  ├─ UserAnalysisAccessEntity.java
│     │     │  │  └─ 映射用户、分析结果、积分账本和请求键之间的访问授权记录。
│     │     │  └─ UserAnalysisAccessMapper.java
│     │     │     └─ 写入和查询用户分析访问权，支持 GET 前的所有权校验。
│     │     │
│     │     ├─ api/
│     │     │  ├─ ApiErrorCode.java
│     │     │  │  └─ 集中定义认证、积分、幂等、加密和上游服务错误码及 HTTP 状态。
│     │     │  ├─ ApiResponse.java
│     │     │  │  └─ 统一封装 code、message、data 与 requestId。
│     │     │  ├─ CreateAnalysisRequest.java
│     │     │  │  └─ 承载并校验用户提交的 Amazon URL。
│     │     │  ├─ GlobalExceptionHandler.java
│     │     │  │  └─ 将领域与安全异常转换为不泄密的统一 JSON 响应。
│     │     │  ├─ ProductAnalysisController.java
│     │     │  │  └─ 暴露需登录的分析 POST/GET，读取用户身份和幂等请求头。
│     │     │  └─ ProductAnalysisView.java
│     │     │     └─ 组织对前端公开的商品摘要、来源和分析内容。
│     │     │
│     │     ├─ auth/
│     │     │  ├─ AuthController.java
│     │     │  │  └─ 提供 session、注册、登录和退出接口，并建立、轮换或销毁 Session。
│     │     │  ├─ InvalidCredentialsException.java
│     │     │  │  └─ 以统一错误表示邮箱或密码认证失败，防止账户枚举。
│     │     │  ├─ LoginRequest.java
│     │     │  │  └─ 登录邮箱和密码 DTO，并对日志输出进行脱敏。
│     │     │  ├─ RegisterRequest.java
│     │     │  │  └─ 注册邮箱和密码 DTO，并对日志输出进行脱敏。
│     │     │  └─ UserSessionView.java
│     │     │     └─ 返回登录状态、用户 ID、邮箱与当前积分。
│     │     │
│     │     ├─ config/
│     │     │  ├─ AppProperties.java
│     │     │  │  └─ 绑定 Rainforest、DeepSeek、缓存和分析参数。
│     │     │  ├─ CacheConfig.java
│     │     │  │  └─ 创建不含用户信息的商品与分析 Caffeine 缓存。
│     │     │  ├─ ExternalCredentialValidator.java
│     │     │  │  └─ 在生产 profile 校验外部 API、AES 密钥和 Secure Cookie 配置。
│     │     │  ├─ HttpClientConfig.java
│     │     │  │  └─ 配置 Rainforest 与 DeepSeek 专用 WebClient、认证头和超时。
│     │     │  └─ RainforestConcurrencyConfig.java
│     │     │     └─ 提供单许可公平信号量，限制单实例 Rainforest 并发。
│     │     │
│     │     ├─ crypto/
│     │     │  ├─ EncryptedText.java
│     │     │  │  └─ 封装 AES-GCM 密文、12-byte IV 和密钥版本。
│     │     │  ├─ EncryptionConfigurationException.java
│     │     │  │  └─ 表示密钥映射、Base64、长度或活动版本配置无效。
│     │     │  ├─ VideoScriptBackfillService.java
│     │     │  │  └─ 按开关分页加密旧口播明文，并以短事务清空旧列。
│     │     │  ├─ VideoScriptCipher.java
│     │     │  │  └─ 使用 AES-256-GCM 与 AAD 加解密口播文案。
│     │     │  ├─ VideoScriptEncryptionException.java
│     │     │  │  └─ 对密文认证、未知版本和密码学运行错误做统一安全封装。
│     │     │  └─ VideoScriptEncryptionProperties.java
│     │     │     └─ 绑定并校验活动密钥、版本映射、回填开关和批量大小。
│     │     │
│     │     ├─ deepseek/
│     │     │  ├─ DeepSeekClient.java
│     │     │  │  └─ 调用 Chat Completions 并分类认证、参数、额度、超时和响应错误。
│     │     │  ├─ DeepSeekException.java
│     │     │  │  └─ 保存 DeepSeek 错误分类和可选上游状态。
│     │     │  ├─ DeepSeekRequest.java
│     │     │  │  └─ 定义模型、消息、Token 上限和 JSON 响应格式请求体。
│     │     │  └─ DeepSeekResponse.java
│     │     │     └─ 封装上游原始 JSON 与提取后的内容文本。
│     │     │
│     │     ├─ observability/
│     │     │  └─ RequestIdFilter.java
│     │     │     └─ 接收或生成 requestId，写入 MDC、响应头并避免敏感头进入日志。
│     │     │
│     │     ├─ persistence/
│     │     │  └─ JsonStringCodec.java
│     │     │     └─ 统一完成 Java 对象与 MySQL JSON 字符串的安全转换。
│     │     │
│     │     ├─ points/
│     │     │  ├─ InsufficientPointsException.java
│     │     │  │  └─ 表示用户无可预占积分。
│     │     │  ├─ PointRequestConflictException.java
│     │     │  │  └─ 区分重复幂等键正在处理或此前已退款。
│     │     │  ├─ PointReservation.java
│     │     │  │  └─ 返回预占状态、账本 ID、已有分析 ID 和剩余积分。
│     │     │  ├─ PointsService.java
│     │     │  │  └─ 以短事务实现积分预占、结算、授权、幂等退款和超时恢复。
│     │     │  ├─ PointTransactionEntity.java
│     │     │  │  └─ 映射注册赠分与分析扣分账本及其状态时间戳。
│     │     │  ├─ PointTransactionMapper.java
│     │     │  │  └─ 执行条件扣款、状态转换、余额更新和超时账本查询 SQL。
│     │     │  ├─ PointTransactionStateException.java
│     │     │  │  └─ 表示账本发生不允许的并发状态转换。
│     │     │  └─ StalePointReservationRecoveryJob.java
│     │     │     └─ 每分钟幂等退款超过配置时限的 RESERVED 记录。
│     │     │
│     │     ├─ product/
│     │     │  ├─ NormalizedProduct.java
│     │     │  │  └─ 与上游格式解耦的标准商品事实模型。
│     │     │  ├─ ProductDataIncompleteException.java
│     │     │  │  └─ 表示商品事实不足以生成可靠分析。
│     │     │  ├─ ProductNormalizer.java
│     │     │  │  └─ 从 Rainforest JSON 提取、清洗并限制商品字段。
│     │     │  ├─ ProductSnapshotEntity.java
│     │     │  │  └─ 映射商品索引、归一化 JSON、上游原始 JSON 和时间戳。
│     │     │  ├─ ProductSnapshotMapper.java
│     │     │  │  └─ 按 Amazon 域名与 ASIN 查询或保存商品快照。
│     │     │  ├─ ProductSnapshotResult.java
│     │     │  │  └─ 绑定快照实体与反序列化后的标准商品。
│     │     │  └─ ProductSnapshotService.java
│     │     │     └─ 按缓存、数据库、演示 JSON、Rainforest 顺序获取和持久化商品。
│     │     │
│     │     ├─ rainforest/
│     │     │  ├─ DemoRainforestResponseProvider.java
│     │     │  │  └─ 识别精确演示 URL 并从 classpath 返回零 credits 样例响应。
│     │     │  ├─ RainforestClient.java
│     │     │  │  └─ 使用受控 WebClient 获取商品并分类上游错误。
│     │     │  ├─ RainforestException.java
│     │     │  │  └─ 表示繁忙、额度、未找到、超时、上游失败或无效响应。
│     │     │  └─ RainforestResponse.java
│     │     │     └─ 封装 Rainforest 原始 JSON、请求标识与 credits 消耗。
│     │     │
│     │     ├─ security/
│     │     │  ├─ ActiveUserFilter.java
│     │     │  │  └─ 对有状态 Session 重新核对数据库 enabled 标记，禁用后销毁会话并返回 401。
│     │     │  ├─ AuthenticationRateLimiter.java
│     │     │  │  └─ 基于 Caffeine 的 IP 独立桶与 IP 加规范化邮箱组合桶限制认证尝试。
│     │     │  ├─ AuthenticationRateLimitException.java
│     │     │  │  └─ 表示登录或注册超过配置频率。
│     │     │  ├─ JsonAccessDeniedHandler.java
│     │     │  │  └─ 将 CSRF 或授权失败转换为 403 统一 JSON。
│     │     │  ├─ JsonAuthenticationEntryPoint.java
│     │     │  │  └─ 将未认证访问转换为 401 统一 JSON。
│     │     │  ├─ SecurityConfig.java
│     │     │  │  └─ 配置 Session、BCrypt、CSRF Cookie、活动用户过滤器、认证端点和 API 访问规则。
│     │     │  └─ SpaCsrfTokenRequestHandler.java
│     │     │     └─ 兼容 SPA 的 CSRF Token 延迟加载、异或响应和请求头解析。
│     │     │
│     │     └─ user/
│     │        ├─ AppUserEntity.java
│     │        │  └─ 映射用户邮箱、BCrypt 哈希、积分、启用状态与时间戳。
│     │        ├─ AppUserMapper.java
│     │        │  └─ 查询用户启用状态并执行积分条件更新。
│     │        ├─ AppUserService.java
│     │        │  └─ 规范化邮箱、校验密码、注册用户和写入赠分账本。
│     │        ├─ AuthenticatedUser.java
│     │        │  └─ 向 Spring Security 暴露用户 ID、邮箱和权限，并在认证后清除 Session 主体中的密码哈希。
│     │        ├─ DatabaseUserDetailsService.java
│     │        │  └─ 从数据库加载认证主体供 AuthenticationManager 校验。
│     │        ├─ EmailAlreadyRegisteredException.java
│     │        │  └─ 表示规范化邮箱唯一键冲突。
│     │        └─ InvalidRegistrationException.java
│     │           └─ 表示邮箱或密码不满足注册约束。
│     │
│     └─ resources/
│        ├─ application.yml
│        │  └─ 配置数据库、Flyway、Session Cookie、日志、外部 API、缓存、加密、认证限流和积分恢复参数。
│        ├─ db/migration/
│        │  ├─ V1__baseline_schema.sql
│        │  │  └─ Flyway 基线，创建原有商品快照与分析结果表。
│        │  └─ V2__user_points_and_video_script_encryption.sql
│        │     └─ 增加用户、积分账本、访问权和口播加密列，并保留迁移期可空明文列。
│        └─ prompts/
│           └─ product-analysis-v1.txt
│              └─ 约束 DeepSeek 只依据商品事实输出固定 JSON 和 150 字内中文口播。
│
├─ deploy/
│  ├─ nginx/
│  │  ├─ default.conf
│  │  │  └─ 本地 HTTP 静态站点/API 代理配置，包含 no-store 和安全响应头。
│  │  └─ https.conf.template
│  │     └─ 生产 Nginx 模板，按域名终止 TLS、跳转 HTTP、代理 API 并设置 HSTS/CSP 等响应头。
│  └─ tls/
│     └─ README.md
│        └─ 说明证书文件名、权限、只读挂载、续期 reload 和私钥不入库要求。
│
├─ docs/
│  ├─ api.md
│  │  └─ 记录 Session/CSRF、注册登录、幂等分析、积分响应、访问权和错误码契约。
│  ├─ architecture.md
│  │  └─ 说明 TLS 边界、认证、积分状态机、分析流、AES-GCM AAD 与两阶段迁移。
│  ├─ project-file-guide.md
│  │  └─ 当前文件；维护所有未被忽略项目文件的目录树与职责。
│  └─ plans/
│     ├─ 2026-07-18-ai-product-analysis-assistant-development-plan.md
│     │  └─ 项目初版前后端、数据库、外部 API 与部署开发计划。
│     └─ 2026-07-22-secure-auth-points-encryption-development-plan.md
│        └─ HTTPS、用户认证、积分账本和口播加密的本次实施计划，不包含测试代码。
│
└─ frontend/
   ├─ Dockerfile
   │  └─ 基于 Nginx 直接复制本地已构建 dist，不在容器内安装或构建。
   ├─ Dockerfile.dockerignore
   │  └─ 前端镜像专用白名单，只保留 dist、Dockerfile 和默认 Nginx 配置。
   ├─ index.html
   │  └─ Vite HTML 入口，定义页面元信息和 Vue 挂载节点。
   ├─ package.json
   │  └─ 锁定 Node/npm、Vue、Element Plus、Axios 和 Vite 版本及脚本。
   ├─ vite.config.js
   │  └─ 配置 Vue 插件、开发服务器和同源 `/api` 代理。
   └─ src/
      ├─ App.vue
      │  └─ 根组件；先加载 session，再切换认证界面或受保护分析工作区。
      ├─ main.js
      │  └─ 创建 Vue 应用、注册 Element Plus 并加载全局主题。
      ├─ api/
      │  ├─ auth.js
      │  │  └─ 封装 session、注册、登录和退出 API。
      │  ├─ http.js
      │  │  └─ 配置 Axios 同源凭据与 XSRF，并统一转换 API 错误和认证事件。
      │  └─ productAnalysis.js
      │     └─ 携带幂等键提交分析、读取授权结果并归一化响应。
      ├─ components/
      │  ├─ AnalysisForm.vue
      │  │  └─ 输入 Amazon URL，展示支持格式，并按积分与提交状态禁用操作。
      │  ├─ AnalysisSections.vue
      │  │  └─ 展示目标用户、场景、痛点和带证据核心卖点。
      │  ├─ AuthGate.vue
      │  │  └─ 提供登录/注册切换、字段校验、提交状态和安全提示。
      │  ├─ ErrorPanel.vue
      │  │  └─ 展示 requestId、认证/积分/幂等/上游错误及处理建议。
      │  ├─ LoadingSteps.vue
      │  │  └─ 展示商品识别、事实整理和 AI 洞察的加载阶段。
      │  ├─ PointsBadge.vue
      │  │  └─ 以紧凑徽标展示当前可用积分。
      │  ├─ ProductSummary.vue
      │  │  └─ 展示商品图片、ASIN、标题、价格、功能与规格。
      │  ├─ UserToolbar.vue
      │  │  └─ 展示脱敏邮箱、积分和退出按钮。
      │  └─ VideoScriptCard.vue
      │     └─ 展示解密后的口播、字符数并提供剪贴板复制。
      ├─ composables/
      │  ├─ useAuth.js
      │  │  └─ 管理 session、注册登录、退出、积分同步及 401/403 状态刷新。
      │  └─ useProductAnalysis.js
      │     └─ 管理分析状态、同一在途幂等键、结果、积分更新和错误重置。
      ├─ model/
      │  ├─ productAnalysis.js
      │  │  └─ 归一化分析提交包装、商品字段和结构化 AI 内容。
      │  └─ userSession.js
      │     └─ 归一化匿名/已登录会话并生成邮箱脱敏显示。
      └─ styles/
         └─ theme.css
            └─ 定义认证卡片、用户工具栏、积分状态、分析页面及响应式视觉样式。
```

## 维护约定

新增、删除或移动未被 `.gitignore` 忽略的项目文件时，应同步更新本目录树。Flyway V3 只有在旧口播完成回填、两项核对均为 0 且再次备份后，才能在第二阶段版本新增；当前文件树故意不包含 V3。
