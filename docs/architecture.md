# 系统架构

## 数据流

```text
浏览器
  -> Nginx
     -> Spring MVC Controller
        -> AmazonUrlParser
        -> AnalysisApplicationService
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
           -> MySQL 分析结果
```

## 为什么 Rainforest 不会与 Spring Boot 冲突

Rainforest 通过标准 HTTPS REST API 提供服务。后端 `pom.xml` 不包含 Rainforest SDK，Rainforest 也不会把任何 Jackson、Reactor、Servlet 或 MyBatis 依赖带入 JVM。因此不存在 Rainforest 版本覆盖 Spring Boot 传递依赖的可能。

集成边界只有 JSON HTTP 契约：

1. `RainforestClient` 使用 Spring Boot 管理的 WebClient。
2. 实际目标固定为配置的 Rainforest base URL 和 `/request`。
3. 用户 URL 不会成为 HTTP 客户端目标，只解析 Amazon 域名和 ASIN。
4. 完整响应先接收为 Jackson `JsonNode`，不要求所有字段与 Java DTO 完全一致。
5. `ProductNormalizer` 只提取标题、品牌、品类、价格、图片、功能和规格等业务字段。
6. 原始 JSON 与归一化 JSON 同时保存在 MySQL，以便上游字段变化后排查或重新处理。

## Spring MVC 与 WebClient

入站请求采用 Spring MVC/Servlet 模式，因为 MyBatis-Plus、JDBC 和信号量都是阻塞式组件。`spring.main.web-application-type=servlet` 明确锁定该行为。

`spring-boot-starter-webflux` 只为出站 WebClient 提供 Reactor Netty。Controller 不返回 `Mono` 或 `Flux`，网络调用在应用服务中同步完成，避免把阻塞数据库操作放入响应式事件循环。

## Rainforest 并发和缓存

`rainforestSemaphore` 是公平的单例 `Semaphore(1, true)`：

- 调用前先检查 Caffeine 和六小时内的 MySQL 快照。
- 等待许可最多 10 秒，超时返回 `RAINFOREST_BUSY`。
- 获得许可后再次检查缓存，合并等待期间已完成的相同请求。
- 外部调用位于 `try/finally` 中，任何错误都会释放许可。
- API key 不写入业务日志。

该约束只在一个 JVM 内有效，所以 Compose 固定一个 backend 服务。多副本部署前必须使用 Redis、数据库或网关实现全局限流。

### 免费额度演示特判

精确 URL `https://www.amazon.com/dp/B073JYC4XM?th=1&psc=1` 由 `DemoRainforestResponseProvider` 处理。它从 classpath 读取 `demo.json`，直接进入商品归一化和持久化步骤，不获取 Rainforest Semaphore，也不调用 Rainforest。

该 URL 会绕过已有商品/分析读取缓存，确保商品事实来自本地 JSON，并继续实时调用 DeepSeek。Maven 只把 `src/main/java/demo.json` 作为额外资源加入 JAR，不会把其他 Java 源文件当作资源复制。

## DeepSeek 内容约束

- 默认模型为 `deepseek-v4-pro`，模型名通过环境变量注入。
- 只向模型发送归一化商品事实，不发送 Rainforest 全量响应。
- 要求 JSON object 输出，卖点必须含 `claim` 和 `evidence`。
- 后端按 Unicode code point 统计口播长度，限制为 150 字符。
- 首次结果无效时只允许一次修复请求，防止无限重试和费用失控。

## 数据与事务

外部网络调用不包含在数据库事务中。只有最终分析结果的查询与写入由短事务包裹。

- `product_snapshot` 以 `amazon_domain + asin` 唯一。
- `analysis_result` 以 `product_snapshot_id + model + prompt_version` 唯一。
- JSON 列在 Java 实体中表示为已校验的字符串，避免引入额外 TypeHandler 依赖。

## 安全边界

- 只接受 HTTPS Amazon URL、允许的 Amazon 域名和标准 443 端口。
- 拒绝用户凭据、IP、localhost 和无法识别的 ASIN。
- 外部服务密钥仅存在于后端环境变量。
- 错误响应不包含堆栈、原始上游响应或配置值。
- requestId 贯穿响应头、响应体与后端日志。

## 镜像构建边界

容器镜像不负责编译项目源码：

- 后端先在本地生成 `backend/target/product-assistant-0.0.1-SNAPSHOT.jar`，后端 Dockerfile 只把该 JAR 复制到 Java 21 JRE 镀像。
- 前端使用 npm 在本地生成 `frontend/dist/`，前端 Dockerfile 只把该目录复制到 Nginx 镜像。
- Dockerfile 专用忽略文件把源码排除在镜像构建上下文之外。
