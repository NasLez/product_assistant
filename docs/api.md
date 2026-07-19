# HTTP API

所有接口以 JSON 通信。成功和失败响应都包含 `requestId`，相同值也会放入 `X-Request-Id` 响应头。

## 创建或读取商品分析

```http
POST /api/v1/product-analyses
Content-Type: application/json
```

请求体：

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
  "requestId": "0e6d71f1-64db-4a95-a9cb-60db2c38c2ea"
}
```

`source`：

- `LIVE`：本次生成并保存。
- `LOCAL_DEMO`：商品数据来自随 JAR 打包的本地 `demo.json`，AI 分析仍由 DeepSeek 实时生成。
- `DATABASE`：读取数据库已有结果。
- `CACHE`：读取进程内缓存。

## 读取已有分析

```http
GET /api/v1/product-analyses/{id}
```

响应结构与创建接口一致，`source` 为 `DATABASE`。不存在时返回 `ANALYSIS_NOT_FOUND`。

## 错误响应

```json
{
  "code": "INVALID_AMAZON_URL",
  "message": "无法从链接中解析 ASIN",
  "data": null,
  "requestId": "0e6d71f1-64db-4a95-a9cb-60db2c38c2ea"
}
```

| HTTP | code | 说明 |
|---:|---|---|
| 400 | `INVALID_AMAZON_URL` | URL 为空、格式不正确、非 HTTPS、站点不支持或没有 ASIN。 |
| 404 | `ANALYSIS_NOT_FOUND` | 分析记录不存在。 |
| 409 | `RAINFOREST_BUSY` | 等待 Rainforest 单请求许可超过 10 秒。 |
| 422 | `PRODUCT_DATA_INCOMPLETE` | Rainforest 没有返回最低必需商品事实。 |
| 429 | `UPSTREAM_QUOTA_EXCEEDED` | Rainforest 或 DeepSeek 额度不足或请求受限。 |
| 502 | `RAINFOREST_ERROR` | Rainforest 上游错误。 |
| 502 | `DEEPSEEK_ERROR` | DeepSeek 上游错误或内容无效。 |
| 504 | `UPSTREAM_TIMEOUT` | 外部服务超时。 |
| 500 | `INTERNAL_ERROR` | 未分类内部错误。 |

## 不对外返回的数据

业务 API 不返回以下内容：

- Rainforest 完整原始 JSON
- DeepSeek 完整原始响应
- API key 或 Authorization header
- 数据库实体内部字段
- 异常堆栈和配置内容
