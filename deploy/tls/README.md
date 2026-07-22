# TLS 证书目录

生产部署前，把由可信 CA 签发且与 `PUBLIC_SERVER_NAME` 匹配的证书链和私钥放入本目录：

```text
deploy/tls/fullchain.pem
deploy/tls/privkey.pem
```

`fullchain.pem` 应包含站点证书及所需中间证书，`privkey.pem` 必须与站点证书匹配。证书文件与私钥已被 `.gitignore` 忽略，只能在运行时以只读方式挂载到 Nginx；不得把它们复制进前端静态目录、后端 JAR、容器镜像或版本库。

在 Linux 服务器上将私钥权限限制为仅部署管理员可读，例如 `chmod 600 deploy/tls/privkey.pem`，并限制项目目录的访问权限。证书续期后替换这两个运行时文件，再执行 `docker compose exec nginx nginx -s reload` 让 Nginx 平滑加载新证书；如容器未运行，则按正常部署流程重新创建 Nginx 容器。

启用 HSTS 前必须确认域名、DNS、证书链和 HTTPS 均真实有效。开发环境若没有可信证书，应使用 Vite 到本地后端的开发代理，并设置 `SESSION_COOKIE_SECURE=false`，不要在公开环境关闭 Secure Cookie。
