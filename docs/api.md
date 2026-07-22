# HTTP API

所有接口都以 JSON 通信，并使用同源 HTTPS。成功和失败响应均采用统一信封，包含 `requestId`；相同值也会写入 `X-Request-Id` 响应头。除匿名会话查询、注册和登录外，`/api/**` 都要求有效的服务端 Session。

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "0e6d71f1-64db-4a95-a9cb-60db2c38c2ea"
}
```

## Session 与 CSRF

浏览器应先调用 `GET /api/v1/auth/session`。该请求会返回当前登录状态，并生成或刷新可由前端读取的 `XSRF-TOKEN` Cookie。Axios 在所有 POST 请求中把该值回传为 `X-XSRF-TOKEN` 请求头；Session Cookie 由浏览器自动携带，JavaScript 不应也无法读取它。

CSRF 校验失败返回 403。前端可以重新查询 session 以刷新 Token，但不得自动重放可能扣积分的 POST 请求。

## 查询当前会话

```http
GET /api/v1/auth/session
```

匿名响应的 `data`：

```json
{
  "authenticated": false,
  "points": 0
}
```

全局 Jackson 配置会省略值为 `null` 的字段，因此匿名响应不包含 `userId` 和 `email`。

已登录响应的 `data`：

```json
{
  "authenticated": true,
  "userId": 12,
  "email": "user@example.com",
  "points": 10
}
```

## 注册

```http
POST /api/v1/auth/register
Content-Type: application/json
X-XSRF-TOKEN: <XSRF-TOKEN Cookie 的值>

{
  "email": "user@example.com",
  "password": "至少十二字节的密码"
}
```

邮箱会执行 trim、Unicode 规范化和小写转换，最长 254 个字符；密码必须为 12～72 UTF-8 bytes。成功时创建 Session、赠送 10 积分、清除登录前 CSRF Token 并返回已登录的会话对象；前端随后查询 session 获取新 Token。数据库只保存 BCrypt 单向哈希，不保存明文密码或可逆密文，认证后 Session 主体也会清除该哈希。

## 登录

```http
POST /api/v1/auth/login
Content-Type: application/json
X-XSRF-TOKEN: <XSRF-TOKEN Cookie 的值>

{
  "email": "user@example.com",
  "password": "用户密码"
}
```

成功后轮换 Session ID、清除登录前 CSRF Token 并返回会话对象；前端随后查询 session 获取新 Token。失败统一返回 `INVALID_CREDENTIALS`，不会泄露邮箱是否已注册。登录和注册同时受来源 IP 独立桶与“来源 IP + 规范化邮箱”组合桶限制，注册成功不会重置 IP 独立桶。

## 退出

```http
POST /api/v1/auth/logout
X-XSRF-TOKEN: <XSRF-TOKEN Cookie 的值>
```

退出会销毁服务端 Session、清除认证状态和旧 CSRF Token，并返回匿名会话对象。前端应随后重新请求 session 获取新 Token。

## 创建或读取商品分析

```http
POST /api/v1/product-analyses
Content-Type: application/json
X-Idempotency-Key: 8e730c93-b982-4e52-aa22-bbe7ee53db06
X-XSRF-TOKEN: <XSRF-TOKEN Cookie 的值>

{
  "amazonUrl": "https://www.amazon.com/dp/B073JYC4XM"
}
```

`X-Idempotency-Key` 必须是 UUID，并在同一次在途请求重试时保持不变。相同用户和相同键只会预占一次积分；新输入或上一请求已明确失败后，前端应生成新键。

成功响应的 `data`：

```json
{
  "result": {
    "id": 1,
    "source": "LIVE",
    "product": {
      "amazonDomain": "amazon.com",
      "asin": "B073JYC4XM",
      "title": "商品标题",
      "brand": "品牌",
      "categoryPath": "Electronics > Memory Cards",
      "price": {
        "amount": 35.95,
        "currency": "USD",
        "display": "$35.95"
      },
      "mainImageUrl": "https://...",
      "features": ["功能一"],
      "specifications": [
        {"name": "容量", "value": "128 GB"}
      ]
    },
    "analysis": {
      "targetUsers": ["目标用户"],
      "useCases": ["使用场景"],
      "painPoints": ["用户痛点"],
      "coreSellingPoints": [
        {"claim": "核心卖点", "evidence": "商品事实依据"}
      ],
      "videoScript": "150 字以内中文口播文案"
    }
  },
  "remainingPoints": 9
}
```

每个成功 POST 扣 1 积分，包括以下所有来源：

- `LIVE`：本次生成并保存。
- `LOCAL_DEMO`：商品数据来自随 JAR 打包的 `demo.json`，AI 分析仍由 DeepSeek 实时生成。
- `DATABASE`：复用数据库已有结果。
- `CACHE`：复用进程内缓存。

积分为 0 时不会启动商品或 AI 处理。请求先创建 `RESERVED` 预占，成功时转为 `SETTLED`；Rainforest、DeepSeek、内容校验、加密或数据库保存失败时转为 `REFUNDED` 并返还积分。

## 读取已有分析

```http
GET /api/v1/product-analyses/{id}
```

当前用户必须在 `user_analysis_access` 中拥有该分析的访问记录。记录不存在或属于其他用户时统一返回 `ANALYSIS_NOT_FOUND`（404），避免泄露其他用户的分析 ID。响应中的分析结构与 POST 的 `result` 相同，`source` 为 `DATABASE`。

## 错误响应

```json
{
  "code": "INVALID_IDEMPOTENCY_KEY",
  "message": "幂等请求键缺失或格式无效",
  "requestId": "0e6d71f1-64db-4a95-a9cb-60db2c38c2ea"
}
```

失败信封中的 `data` 为 `null`，会被全局 JSON 规则省略。

| HTTP | code | 说明 |
|---:|---|---|
| 400 | `INVALID_AMAZON_URL` | URL 为空、格式不正确、非 HTTPS、站点不支持或没有 ASIN。 |
| 400 | `INVALID_IDEMPOTENCY_KEY` | `X-Idempotency-Key` 缺失或不是 UUID。 |
| 400 | `INVALID_REGISTRATION` | 邮箱或密码不符合注册约束。 |
| 401 | `AUTHENTICATION_REQUIRED` | 未登录、Session 已过期或账号已被禁用。 |
| 401 | `INVALID_CREDENTIALS` | 邮箱或密码错误。 |
| 402 | `INSUFFICIENT_POINTS` | 当前积分不足。 |
| 403 | `ACCESS_DENIED` | CSRF 或权限校验失败。 |
| 404 | `ANALYSIS_NOT_FOUND` | 分析不存在或当前用户没有访问权。 |
| 409 | `EMAIL_ALREADY_REGISTERED` | 邮箱已注册。 |
| 409 | `REQUEST_IN_PROGRESS` | 相同幂等键仍在处理。 |
| 409 | `REQUEST_ALREADY_FAILED` | 相同幂等键对应的请求已经失败并退款。 |
| 409 | `RAINFOREST_BUSY` | 等待 Rainforest 单请求许可超过 10 秒。 |
| 422 | `PRODUCT_DATA_INCOMPLETE` | Rainforest 没有返回最低必需商品事实。 |
| 429 | `AUTH_RATE_LIMITED` | 登录或注册尝试过于频繁。 |
| 429 | `UPSTREAM_QUOTA_EXCEEDED` | Rainforest 或 DeepSeek 额度不足或请求受限。 |
| 500 | `ENCRYPTION_ERROR` | 服务端加密配置错误或密文认证失败。 |
| 500 | `INTERNAL_ERROR` | 未分类内部错误。 |
| 502 | `RAINFOREST_ERROR` | Rainforest 上游错误。 |
| 502 | `DEEPSEEK_AUTHENTICATION_FAILED` | DeepSeek API Key 无效或无模型权限。 |
| 502 | `DEEPSEEK_INVALID_REQUEST` | DeepSeek 模型或请求参数配置无效。 |
| 502 | `DEEPSEEK_ERROR` | DeepSeek 上游错误或内容无效。 |
| 504 | `UPSTREAM_TIMEOUT` | 外部服务超时。 |

## 不对外返回的数据

业务 API 不返回或记录以下敏感内容：

- 密码、BCrypt 输入、Session ID 和 CSRF Token
- AES 密钥、完整 IV、完整密文和解密后的口播明文日志
- Rainforest 完整原始 JSON、DeepSeek 完整原始响应和外部 API key
- 数据库实体内部字段、异常堆栈和配置内容
