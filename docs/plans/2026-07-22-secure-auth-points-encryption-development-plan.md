# Secure Authentication, Points, and Encryption Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为现有 Amazon AI 产品分析助手增加 HTTPS 传输保护、邮箱密码用户体系、登录访问控制、10 积分赠送与成功分析扣费，以及口播文案的 AES-256-GCM 数据库加密存储。

**Architecture:** 保持 Vue SPA、Nginx、Spring MVC、MyBatis-Plus、MySQL 的单体部署结构。浏览器与 Nginx 之间统一使用 TLS 1.2/1.3；认证采用 Spring Security 服务端 Session、HttpOnly Cookie 和 CSRF Token；密码使用 BCrypt 单向哈希；口播文案使用服务端 AES-256-GCM 加密。积分采用数据库原子预占、成功结算、失败退款和超时预占恢复，避免把外部 API 调用放进长数据库事务。

**Tech Stack:** Java 21、Spring Boot 3.5.16、Spring MVC、Spring Security、Spring WebClient、MyBatis-Plus 3.5.17、Flyway、MySQL 8.4、Vue 3.5、Axios、Element Plus、Nginx、Docker Compose。

**Test-code constraint:** 本计划不创建或修改任何单元测试、集成测试、前端组件测试或端到端测试文件，也不包含测试代码。验收部分只列出人工检查与数据库核对项。

---

## 1. 需求解释与关键决策

### 1.1 传输加密

浏览器不能安全保存用于自定义前端加密的长期对称密钥，因此不设计 JavaScript 自定义加密协议，统一使用浏览器原生 HTTPS/TLS：

```text
Browser
  └─ HTTPS / TLS 1.2 or 1.3
      └─ Nginx（TLS 终止、证书、HSTS、安全响应头）
          └─ Docker 私有 bridge 网络
              └─ Spring Boot :8080（不映射宿主机端口）
```

TLS 同时保护前端请求和后端响应的机密性、完整性与服务端身份认证。生产环境 HTTP 80 只做 HTTPS 301 跳转，API 响应增加 `Cache-Control: no-store`。Nginx 到 Spring Boot 位于同一主机的私有 Docker 网络，不额外引入容器间证书管理。

### 1.2 用户认证

采用 Spring Security 服务端 Session：

- Session ID 使用 `HttpOnly; Secure; SameSite=Lax` Cookie，前端 JavaScript 无法读取。
- SPA 使用 `CookieCsrfTokenRepository`，Axios 将 `XSRF-TOKEN` 回传为 `X-XSRF-TOKEN`。
- 登录成功轮换 Session ID，退出时销毁 Session 并清除认证状态。
- auth 接口按需匿名，其余 `/api/**` 必须登录。
- 密码使用 BCrypt 单向哈希，不允许可逆加密或明文存储。

### 1.3 积分语义

- 注册成功赠送 10 积分，并写入积分账本。
- 每个成功返回分析结果的 POST 请求扣 1 分，包括 `LIVE`、`LOCAL_DEMO`、`DATABASE` 和 `CACHE` 来源。
- Rainforest、DeepSeek、归一化、AI 校验、数据库保存或加密任一环节失败，最终不扣积分。
- 积分为 0 时不启动商品或 AI 处理，返回 `INSUFFICIENT_POINTS`。
- 前端每次提交生成 UUID `X-Idempotency-Key`，相同用户和相同键只产生一笔积分预占。
- 请求开始时原子预占 1 分并记为 `RESERVED`；成功转为 `SETTLED`，失败加回并转为 `REFUNDED`。
- 定时回收超过 15 分钟仍为 `RESERVED` 的异常中断记录。

### 1.4 口播文案静态加密

- 算法：`AES/GCM/NoPadding`，256-bit 密钥。
- 每条记录用 `SecureRandom` 生成独立 12-byte IV，GCM Tag 为 128 bit。
- 密文、IV、密钥版本分列保存；数据库和前端均不保存密钥。
- AAD 使用 `analysis-result:{productSnapshotId}:{model}:{promptVersion}:{keyVersion}`。
- 环境变量提供活动密钥和历史版本映射，为密钥轮换保留解密能力。
- 日志、异常和迁移输出禁止打印口播明文、密钥、IV 或完整密文。

## 2. 文件变更总览

### 2.1 后端新增

```text
backend/src/main/java/com/example/productassistant/
├─ auth/
│  ├─ AuthController.java
│  ├─ LoginRequest.java
│  ├─ RegisterRequest.java
│  └─ UserSessionView.java
├─ security/
│  ├─ SecurityConfig.java
│  ├─ AuthenticationRateLimiter.java
│  ├─ JsonAuthenticationEntryPoint.java
│  ├─ JsonAccessDeniedHandler.java
│  └─ SpaCsrfTokenRequestHandler.java
├─ user/
│  ├─ AppUserEntity.java
│  ├─ AppUserMapper.java
│  ├─ AppUserService.java
│  └─ DatabaseUserDetailsService.java
├─ points/
│  ├─ InsufficientPointsException.java
│  ├─ PointReservation.java
│  ├─ PointTransactionEntity.java
│  ├─ PointTransactionMapper.java
│  ├─ PointsService.java
│  └─ StalePointReservationRecoveryJob.java
├─ crypto/
│  ├─ EncryptedText.java
│  ├─ EncryptionConfigurationException.java
│  ├─ VideoScriptCipher.java
│  ├─ VideoScriptBackfillService.java
│  └─ VideoScriptEncryptionProperties.java
└─ analysis/
   ├─ AnalysisSubmissionService.java
   ├─ AnalysisSubmissionView.java
   ├─ UserAnalysisAccessEntity.java
   └─ UserAnalysisAccessMapper.java

backend/src/main/resources/db/migration/
├─ V1__baseline_schema.sql
├─ V2__user_points_and_video_script_encryption.sql
└─ V3__drop_plaintext_video_script.sql    # 第二阶段发布，回填核对前不得加入第一阶段 JAR
```

### 2.2 后端修改

```text
backend/pom.xml
backend/src/main/resources/application.yml
backend/src/main/java/com/example/productassistant/ProductAssistantApplication.java
backend/src/main/java/com/example/productassistant/config/ExternalCredentialValidator.java
backend/src/main/java/com/example/productassistant/api/ApiErrorCode.java
backend/src/main/java/com/example/productassistant/api/GlobalExceptionHandler.java
backend/src/main/java/com/example/productassistant/api/ProductAnalysisController.java
backend/src/main/java/com/example/productassistant/analysis/AnalysisApplicationService.java
backend/src/main/java/com/example/productassistant/analysis/AnalysisResultEntity.java
backend/src/main/java/com/example/productassistant/analysis/AnalysisResultMapper.java
```

### 2.3 前端新增与修改

```text
Create frontend/src/api/auth.js
Create frontend/src/composables/useAuth.js
Create frontend/src/model/userSession.js
Create frontend/src/components/AuthGate.vue
Create frontend/src/components/UserToolbar.vue
Modify frontend/src/api/http.js
Modify frontend/src/api/productAnalysis.js
Modify frontend/src/composables/useProductAnalysis.js
Modify frontend/src/model/productAnalysis.js
Modify frontend/src/components/AnalysisForm.vue
Modify frontend/src/components/ErrorPanel.vue
Modify frontend/src/App.vue
Modify frontend/src/styles/theme.css
```

### 2.4 部署与文档

```text
Modify .env.example
Modify .gitignore
Modify compose.yaml
Modify deploy/nginx/default.conf
Create deploy/nginx/https.conf.template
Create deploy/tls/README.md
Modify README.md
Modify docs/api.md
Modify docs/architecture.md
Modify docs/project-file-guide.md
```

## 3. Task 1：引入 Spring Security 与 Flyway

**Files:**

- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__baseline_schema.sql`
- Modify: `compose.yaml`

**Steps:**

1. 在 `pom.xml` 增加 `spring-boot-starter-security`、`flyway-core` 和 `flyway-mysql`，版本继续由 Spring Boot 管理。
2. 将当前 `database/init.sql` 的两张基础表 DDL复制到 `V1__baseline_schema.sql`，作为新数据库基线。
3. 配置 Flyway 和 Session：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 1
    validate-on-migrate: true
  sql:
    init:
      mode: never
server:
  servlet:
    session:
      timeout: 8h
      cookie:
        http-only: true
        secure: ${SESSION_COOKIE_SECURE:false}
        same-site: lax
```

4. 从 `compose.yaml` 删除 `database/init.sql` 的 MySQL init 挂载，并在 V1 基线落地后删除 `database/init.sql`，避免与 Flyway 双重维护。
5. 现有非空数据库首次启动 baseline 为 V1 后执行 V2；全新空数据库依次执行 V1、V2。

## 4. Task 2：数据库 V2 升级迁移

**Files:**

- Create: `backend/src/main/resources/db/migration/V2__user_points_and_video_script_encryption.sql`
- Create in phase-two release only: `backend/src/main/resources/db/migration/V3__drop_plaintext_video_script.sql`
- Delete after V1 is created: `database/init.sql`

### 4.1 V2 完整 SQL

```sql
ALTER TABLE analysis_result
    MODIFY COLUMN video_script VARCHAR(600) NULL,
    ADD COLUMN video_script_ciphertext BLOB NULL AFTER video_script,
    ADD COLUMN video_script_iv BINARY(12) NULL AFTER video_script_ciphertext,
    ADD COLUMN video_script_key_version VARCHAR(32) NULL AFTER video_script_iv;

CREATE TABLE app_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    points INT UNSIGNED NOT NULL DEFAULT 10,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email),
    CONSTRAINT chk_app_user_points CHECK (points >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE point_transaction (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    transaction_type VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    delta INT NOT NULL,
    balance_after INT UNSIGNED NOT NULL,
    analysis_result_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    settled_at DATETIME(3) NULL,
    refunded_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_transaction_user_request (user_id, request_id),
    KEY idx_point_transaction_status_created (status, created_at),
    KEY idx_point_transaction_analysis (analysis_result_id),
    CONSTRAINT fk_point_transaction_user
        FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_point_transaction_analysis
        FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id),
    CONSTRAINT chk_point_transaction_type
        CHECK (transaction_type IN ('REGISTER_BONUS', 'ANALYSIS_DEBIT')),
    CONSTRAINT chk_point_transaction_status
        CHECK (status IN ('SETTLED', 'RESERVED', 'REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_analysis_access (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    analysis_result_id BIGINT UNSIGNED NOT NULL,
    point_transaction_id BIGINT UNSIGNED NOT NULL,
    request_id CHAR(36)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_analysis_access_request (user_id, request_id),
    KEY idx_user_analysis_access_result (user_id, analysis_result_id),
    CONSTRAINT fk_user_analysis_access_user
        FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_analysis_access_result
        FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id),
    CONSTRAINT fk_user_analysis_access_transaction
        FOREIGN KEY (point_transaction_id) REFERENCES point_transaction(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 4.2 第二阶段 V3 明文列移除 SQL

第一阶段开发和发布时不得把 V3 放进 JAR。只有目标环境完成应用层回填，且以下两个查询均返回 0 后，才在第二阶段版本中新建 V3，由 Flyway 正式执行并记录版本：

```sql
SELECT COUNT(*) AS not_encrypted_rows
FROM analysis_result
WHERE video_script_ciphertext IS NULL
   OR video_script_iv IS NULL
   OR video_script_key_version IS NULL;

SELECT COUNT(*) AS remaining_plaintext_rows
FROM analysis_result
WHERE video_script IS NOT NULL;

-- 仅当上面两个查询都返回 0 时执行。
ALTER TABLE analysis_result
    MODIFY COLUMN video_script_ciphertext BLOB NOT NULL,
    MODIFY COLUMN video_script_iv BINARY(12) NOT NULL,
    MODIFY COLUMN video_script_key_version VARCHAR(32) NOT NULL,
    DROP COLUMN video_script;
```

V3 文件顶部必须写明备份、核对和发布顺序。禁止绕过 Flyway 手工执行 ALTER，否则数据库实际结构会与 `flyway_schema_history` 不一致。

## 5. Task 3：实现 AES-256-GCM 口播加密

**Files:**

- Create: `backend/src/main/java/com/example/productassistant/crypto/EncryptedText.java`
- Create: `backend/src/main/java/com/example/productassistant/crypto/VideoScriptEncryptionProperties.java`
- Create: `backend/src/main/java/com/example/productassistant/crypto/VideoScriptCipher.java`
- Create: `backend/src/main/java/com/example/productassistant/crypto/EncryptionConfigurationException.java`
- Modify: `backend/src/main/java/com/example/productassistant/config/ExternalCredentialValidator.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `.env.example`
- Modify: `compose.yaml`

**Required API:**

```java
public record EncryptedText(byte[] ciphertext, byte[] iv, String keyVersion) {
}

public interface VideoScriptCipher {
    EncryptedText encrypt(String plaintext, String associatedData);
    String decrypt(EncryptedText encrypted, String associatedData);
}
```

**Steps:**

1. 配置类读取活动密钥版本和 `version=base64Key` 映射。
2. 启动时拒绝空密钥、重复版本、非法 Base64、解码后不是 32 bytes 或活动版本不存在。
3. 加密使用 `Cipher.getInstance("AES/GCM/NoPadding")`、`GCMParameterSpec(128, iv)` 和每次新生成的 12-byte IV。
4. 解密根据记录中的 keyVersion 选密钥；未知版本或 GCM Tag 失败时只返回通用内部错误。
5. `.env.example` 新增：

```dotenv
VIDEO_SCRIPT_ACTIVE_KEY_VERSION=v1
VIDEO_SCRIPT_ENCRYPTION_KEYS=v1=replace_with_base64_encoded_32_byte_key
VIDEO_SCRIPT_BACKFILL_ENABLED=false
SESSION_COOKIE_SECURE=false
```

6. 以上密钥变量只注入 backend，禁止使用 `VITE_` 前缀或进入 frontend/nginx 镜像。

## 6. Task 4：改造分析读写并回填旧明文

**Files:**

- Modify: `backend/src/main/java/com/example/productassistant/analysis/AnalysisResultEntity.java`
- Modify: `backend/src/main/java/com/example/productassistant/analysis/AnalysisApplicationService.java`
- Modify: `backend/src/main/java/com/example/productassistant/analysis/AnalysisResultMapper.java`
- Create: `backend/src/main/java/com/example/productassistant/crypto/VideoScriptBackfillService.java`

**Steps:**

1. Entity 增加 `byte[] videoScriptCiphertext`、`byte[] videoScriptIv`、`String videoScriptKeyVersion`；V3 前暂留可空旧字段。
2. 保存时用商品 ID、模型、Prompt 版本和 keyVersion 构造 AAD，加密口播，只写密文列并把旧明文写 NULL。
3. 读取时只在服务层解密并恢复 `ProductAnalysis.videoScript`；Controller、Mapper 日志不得接触密钥。
4. 回填服务分页查询“密文为空且旧明文非空”的记录，逐条加密、更新密文字段并清空旧明文，每批短事务。
5. 只有 `VIDEO_SCRIPT_BACKFILL_ENABLED=true` 才运行回填；日志只输出数量和失败 ID。
6. 核对为 0 后执行 V3，后续版本再删除旧字段兼容代码和回填服务。

## 7. Task 5：邮箱注册、登录、Session 与 CSRF

**Files:**

- Create: `backend/src/main/java/com/example/productassistant/user/AppUserEntity.java`
- Create: `backend/src/main/java/com/example/productassistant/user/AppUserMapper.java`
- Create: `backend/src/main/java/com/example/productassistant/user/AppUserService.java`
- Create: `backend/src/main/java/com/example/productassistant/user/DatabaseUserDetailsService.java`
- Create: `backend/src/main/java/com/example/productassistant/auth/RegisterRequest.java`
- Create: `backend/src/main/java/com/example/productassistant/auth/LoginRequest.java`
- Create: `backend/src/main/java/com/example/productassistant/auth/UserSessionView.java`
- Create: `backend/src/main/java/com/example/productassistant/auth/AuthController.java`
- Create: `backend/src/main/java/com/example/productassistant/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/example/productassistant/security/AuthenticationRateLimiter.java`
- Create: `backend/src/main/java/com/example/productassistant/security/JsonAuthenticationEntryPoint.java`
- Create: `backend/src/main/java/com/example/productassistant/security/JsonAccessDeniedHandler.java`
- Create: `backend/src/main/java/com/example/productassistant/security/SpaCsrfTokenRequestHandler.java`

**Endpoints:**

```text
GET  /api/v1/auth/session   匿名可访问；返回登录状态、用户、积分并刷新 CSRF Cookie
POST /api/v1/auth/register  匿名可访问；注册并赠送 10 积分
POST /api/v1/auth/login     匿名可访问；认证并建立 Session
POST /api/v1/auth/logout    已登录；注销 Session 并刷新 CSRF Token
```

**Steps:**

1. 邮箱 trim、Unicode 规范化并按 `Locale.ROOT` 转小写，最大 254 字符。
2. 密码限制 12～72 UTF-8 bytes，请求 DTO 不生成包含密码的字符串。
3. 使用 `BCryptPasswordEncoder(12)`；数据库只保存哈希。
4. 注册事务内插入 points=10 的用户和 `REGISTER_BONUS / SETTLED / delta=10` 账本。
5. 登录失败统一返回“邮箱或密码错误”，避免账户枚举。
6. 登录成功轮换 Session ID并保存 SecurityContext。
7. 静态资源和 auth 入口按需放行，其余 `/api/**` 认证；关闭 HTTP Basic 和默认表单登录，保持 CSRF 开启。
8. Session Cookie 为 HttpOnly；只有 CSRF Cookie 允许前端读取。
9. 认证失败返回 401 JSON，授权或 CSRF 失败返回 403 JSON，复用 ApiResponse 和 requestId。
10. 注册、登录增加 Caffeine 单实例速率限制，超限返回 429。

## 8. Task 6：积分预占、结算、退款和恢复

**Files:**

- Create: `backend/src/main/java/com/example/productassistant/points/PointTransactionEntity.java`
- Create: `backend/src/main/java/com/example/productassistant/points/PointTransactionMapper.java`
- Create: `backend/src/main/java/com/example/productassistant/points/PointReservation.java`
- Create: `backend/src/main/java/com/example/productassistant/points/PointsService.java`
- Create: `backend/src/main/java/com/example/productassistant/points/InsufficientPointsException.java`
- Create: `backend/src/main/java/com/example/productassistant/points/StalePointReservationRecoveryJob.java`
- Modify: `backend/src/main/java/com/example/productassistant/ProductAssistantApplication.java`

**Atomic reservation SQL:**

```sql
UPDATE app_user
SET points = points - 1
WHERE id = #{userId}
  AND enabled = 1
  AND points > 0;
```

只有受影响行数为 1 才插入 `ANALYSIS_DEBIT / RESERVED / delta=-1`；否则回滚并返回积分不足。

```text
reserve(userId, requestId)
  ├─ duplicate SETTLED -> 返回既有 analysisResultId，不重复扣费
  ├─ duplicate RESERVED -> REQUEST_IN_PROGRESS
  ├─ duplicate REFUNDED -> REQUEST_ALREADY_FAILED
  ├─ points == 0        -> INSUFFICIENT_POINTS
  └─ points > 0         -> points - 1 + RESERVED

success -> SETTLED + analysisResultId + user access
failure -> REFUNDED + points + 1
crash   -> 定时回收超时 RESERVED
```

1. reserve、settle、refund 各用短事务，不跨越 Rainforest/DeepSeek 调用。
2. refund 使用状态条件更新，只有 `RESERVED -> REFUNDED` 才能加回积分。
3. settle 只有 `RESERVED -> SETTLED` 可以成功。
4. 每分钟扫描超过 15 分钟的 RESERVED，逐笔幂等退款；启用 Spring Scheduling。

## 9. Task 7：接入分析接口与用户访问权

**Files:**

- Create: `backend/src/main/java/com/example/productassistant/analysis/AnalysisSubmissionService.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/AnalysisSubmissionView.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/UserAnalysisAccessEntity.java`
- Create: `backend/src/main/java/com/example/productassistant/analysis/UserAnalysisAccessMapper.java`
- Modify: `backend/src/main/java/com/example/productassistant/api/ProductAnalysisController.java`
- Modify: `backend/src/main/java/com/example/productassistant/analysis/AnalysisApplicationService.java`

```text
Controller 读取 userId + X-Idempotency-Key
  -> reserve
  -> analyze
  -> 同一事务内 settle + 写 user_analysis_access
  -> 返回 AnalysisSubmissionView
catch failure
  -> refund
  -> 原异常继续交给 GlobalExceptionHandler
```

1. `X-Idempotency-Key` 必须是 UUID，缺失或非法返回 400。
2. POST 返回包装结构：

```json
{
  "result": {
    "id": 1,
    "source": "LIVE",
    "product": {},
    "analysis": {}
  },
  "remainingPoints": 9
}
```

3. GET 按 ID 查询必须先检查 `user_analysis_access`；未授权统一返回 404，避免泄露其他用户分析 ID。
4. 分析缓存只保存无用户信息的 ProductAnalysisView，禁止缓存用户 ID、积分或 Session。

## 10. Task 8：错误码和安全日志

**Files:**

- Modify: `backend/src/main/java/com/example/productassistant/api/ApiErrorCode.java`
- Modify: `backend/src/main/java/com/example/productassistant/api/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/example/productassistant/observability/RequestIdFilter.java`

| HTTP | code | 含义 |
|---:|---|---|
| 400 | `INVALID_IDEMPOTENCY_KEY` | 请求键缺失或非法 |
| 400 | `INVALID_REGISTRATION` | 邮箱或密码格式错误 |
| 401 | `AUTHENTICATION_REQUIRED` | 未登录或 Session 过期 |
| 401 | `INVALID_CREDENTIALS` | 邮箱或密码错误 |
| 402 | `INSUFFICIENT_POINTS` | 积分不足 |
| 403 | `ACCESS_DENIED` | CSRF 或权限校验失败 |
| 409 | `EMAIL_ALREADY_REGISTERED` | 邮箱已注册 |
| 409 | `REQUEST_IN_PROGRESS` | 相同幂等键正在处理 |
| 429 | `AUTH_RATE_LIMITED` | 登录或注册过于频繁 |
| 500 | `ENCRYPTION_ERROR` | 加密配置或密文认证失败 |

日志允许记录 userId、requestId、分析 ID、账本 ID、错误分类和 keyVersion；禁止记录完整邮箱、密码、Session ID、CSRF Token、密钥和口播明文。

## 11. Task 9：前端登录注册与 Session

**Files:**

- Create: `frontend/src/api/auth.js`
- Create: `frontend/src/composables/useAuth.js`
- Create: `frontend/src/model/userSession.js`
- Create: `frontend/src/components/AuthGate.vue`
- Create: `frontend/src/components/UserToolbar.vue`
- Modify: `frontend/src/api/http.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/styles/theme.css`

**Steps:**

1. Axios 设置 `withCredentials: true`、`xsrfCookieName: 'XSRF-TOKEN'`、`xsrfHeaderName: 'X-XSRF-TOKEN'`，保持同源 `/api`。
2. 启动先调用 `GET /v1/auth/session`，取得登录状态、积分和 CSRF Cookie。
3. AuthGate 提供登录/注册切换、邮箱密码校验、提交状态和通用错误；密码不写入任何浏览器存储。
4. 未登录时不挂载商品分析表单。
5. UserToolbar 展示脱敏邮箱、积分和退出按钮。
6. 登录和退出后重新请求 session，刷新被 Spring Security 清理的 CSRF Token。
7. 401 清空认证态；403 刷新 session/CSRF，但不自动重放扣费请求。

## 12. Task 10：前端积分与幂等提交

**Files:**

- Modify: `frontend/src/api/productAnalysis.js`
- Modify: `frontend/src/composables/useProductAnalysis.js`
- Modify: `frontend/src/model/productAnalysis.js`
- Modify: `frontend/src/components/AnalysisForm.vue`
- Modify: `frontend/src/components/ErrorPanel.vue`
- Modify: `frontend/src/App.vue`

**Steps:**

1. 用户主动提交时通过 `crypto.randomUUID()` 生成 `X-Idempotency-Key`。
2. 同一在途请求始终复用同一个键；成功、明确失败或用户重新输入后才生成新键。
3. 解析 AnalysisSubmissionView，把 result 交给现有组件，把 remainingPoints 同步到 useAuth。
4. 积分为 0 时禁用提交并显示提示，服务端仍是最终边界。
5. 增加认证、积分、CSRF 和幂等冲突提示。
6. 不缓存密码、Session ID、密钥或完整分析响应。

## 13. Task 11：Nginx HTTPS 和安全响应头

**Files:**

- Create: `deploy/nginx/https.conf.template`
- Create: `deploy/tls/README.md`
- Modify: `deploy/nginx/default.conf`
- Modify: `compose.yaml`
- Modify: `.gitignore`
- Modify: `.env.example`

**Production configuration core:**

```nginx
server {
    listen 80;
    server_name ${PUBLIC_SERVER_NAME};
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    http2 on;
    server_name ${PUBLIC_SERVER_NAME};

    ssl_certificate /etc/nginx/tls/fullchain.pem;
    ssl_certificate_key /etc/nginx/tls/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:TLS:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "no-referrer" always;
    add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Request-Id $request_id;
        proxy_read_timeout 120s;
        add_header Cache-Control "no-store" always;
    }
}
```

1. 实际 `https.conf.template` 合并现有 SPA、assets、代理头和超时配置。
2. Compose 把模板挂载到 `/etc/nginx/templates/default.conf.template`，设置 `PUBLIC_SERVER_NAME`，并将 `NGINX_ENVSUBST_FILTER` 限制为 `PUBLIC_SERVER_NAME`，避免 `$host`、`$request_uri` 等 Nginx 变量被错误替换。
3. Compose 映射 80/443并挂载证书；backend 不暴露宿主机端口。
4. `.gitignore` 忽略 `deploy/tls/*.pem` 和 `*.key`，保留 README；证书私钥只作为运行时只读挂载，不进入前端镜像。
5. TLS README 说明证书文件名、权限、续期 reload 和私钥不得打进镜像。
6. Nginx 的 `add_header` 在 location 中会覆盖父级继承，因此 `/api/` 增加 `Cache-Control` 时必须同时重复公共安全头，确保 API 不丢失 HSTS、CSP 等头。
7. 本地允许 Secure Cookie=false；生产必须为 true。HSTS 仅在真实有效 HTTPS 域名上线后启用。

## 14. Task 12：部署配置与凭据校验

**Files:**

- Modify: `.env.example`
- Modify: `compose.yaml`
- Modify: `backend/src/main/java/com/example/productassistant/config/ExternalCredentialValidator.java`
- Modify: `backend/src/main/resources/application.yml`

```dotenv
PUBLIC_SERVER_NAME=example.com
SESSION_COOKIE_SECURE=true
VIDEO_SCRIPT_ACTIVE_KEY_VERSION=v1
VIDEO_SCRIPT_ENCRYPTION_KEYS=v1=replace_with_base64_encoded_32_byte_key
VIDEO_SCRIPT_BACKFILL_ENABLED=false
AUTH_RATE_LIMIT_ATTEMPTS=10
AUTH_RATE_LIMIT_WINDOW=10m
POINT_RESERVATION_TIMEOUT=15m
```

1. prod Profile 缺少活动 AES 密钥或 Secure Cookie 配置时拒绝启动。
2. 密钥只存 `.env`、Docker Secret 或外部 Secret Manager；示例文件只放占位值。
3. 数据库升级前备份 MySQL volume 或执行一致性 SQL dump。
4. 先发布“旧明文读取 + 新密文写入”，完成回填后才执行 V3。

## 15. Task 13：更新文档

**Files:**

- Modify: `README.md`
- Modify: `docs/api.md`
- Modify: `docs/architecture.md`
- Modify: `docs/project-file-guide.md`

1. README 增加证书、Cookie、AES 密钥、数据库迁移和密钥轮换说明。
2. API 文档增加 auth 接口、幂等请求头、积分响应、认证错误及 GET 访问权规则。
3. 架构文档增加 TLS 边界、Session/CSRF、积分状态机、AES-GCM AAD 和两阶段迁移。
4. 文件职责树补充所有新增文件。
5. 明确密码使用单向哈希，只有口播文案使用对称加密。

## 16. 数据迁移与上线顺序

1. 备份现有 MySQL 数据。
2. 准备有效域名证书和 32-byte Base64 AES 密钥，限制私钥与 `.env` 权限。
3. 发布包含 Flyway V1/V2、双读单写加密、用户、积分和认证功能的后端。
4. Flyway 对旧库 baseline 为 V1 并执行 V2；新库依次执行 V1/V2。
5. 发布认证前端和 HTTPS Nginx 配置。
6. 设置 `VIDEO_SCRIPT_BACKFILL_ENABLED=true`，执行旧口播分页加密。
7. 执行两个只读核对 SQL，确认未加密行和残留明文行都是 0。
8. 再次备份，发布包含 V3 的第二阶段后端，由 Flyway 执行并记录删除明文列。
9. 关闭回填开关，下一版本删除旧字段兼容代码。

## 17. 人工验收清单（无测试代码）

- HTTP 跳转 HTTPS，证书域名正确，只允许 TLS 1.2/1.3。
- 未登录访问分析接口返回 401，页面只显示认证入口。
- 注册后数据库只保存 BCrypt 哈希，余额为 10，存在 REGISTER_BONUS 账本。
- Session Cookie 具有 HttpOnly、Secure、SameSite；缺少 CSRF Token 的修改请求返回 403。
- 成功分析一次余额从 10 变为 9，账本从 RESERVED 进入 SETTLED。
- Rainforest 或 DeepSeek 失败时积分恢复，账本进入 REFUNDED。
- 并发请求不能让积分小于 0，相同幂等键不重复扣费。
- 用户不能读取未授权的其他用户分析记录。
- analysis_result 只保存密文、12-byte IV 和 keyVersion，不写入新明文。
- 修改密文、IV 或 AAD 后解密失败，API 不泄露加密细节。
- API 响应包含 no-store、HSTS、CSP、nosniff 和 frame protection。
- 日志不出现密码、Session、CSRF Token、AES 密钥或口播明文。

## 18. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 前端自定义密钥被提取 | 不自定义加密协议，使用标准 TLS |
| AES-GCM 重复 IV | 每条记录使用 SecureRandom 独立 IV |
| 密钥丢失 | 版本化密钥映射，安全备份并保留历史密钥 |
| 并发透支积分 | 条件 UPDATE、InnoDB 行锁、唯一幂等键 |
| 外部 API 失败扣费 | RESERVED/SETTLED/REFUNDED 和补偿退款 |
| 进程崩溃遗留预占 | 定时回收超时 RESERVED |
| 缓存泄露用户数据 | 缓存无用户字段，访问权单独落库 |
| Cookie 被脚本窃取 | HttpOnly、Secure、SameSite |
| CSRF | Spring Security CSRF Cookie/Header |
| 迁移提前丢失明文 | 双读单写、分页回填、SQL 核对、V3 手工执行 |

## 19. 设计依据

- [Spring Security Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [Spring Security SPA CSRF](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html)
- [Spring Boot Flyway Database Migrations](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [OWASP Transport Layer Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html)
- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
- [MySQL 8.4 InnoDB Locking](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html)

---

执行时必须坚持：不把长期密钥放入前端、不保存明文密码、不在外部 API 调用期间持有数据库事务、不在积分失败补偿中执行非幂等加款、不在完成加密回填前删除旧明文列。
