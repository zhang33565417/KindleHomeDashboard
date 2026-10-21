# 公网部署：Nginx 账号密码认证

本方案不修改 Java、前端或本地 JSON 数据结构，不增加数据库。适用于 **Linux 服务器，Nginx 与 Java 应用在同一台主机**。如果使用宝塔、容器或已有站点，应将对应配置合并到实际站点，不能直接重复添加同域名的 server 块。

访问链路：手机或 Kindle → HTTPS Nginx → `127.0.0.1:8080` Java 应用。

## 访问范围

| 路径 | 访问要求 |
|---|---|
| `/`、页面脚本及样式 | 账号密码 |
| 所有业务 API（包括 `/api/health`） | 账号密码 |
| 精确路径 `/api/image` 的 GET / HEAD | 无需账号密码 |
| `/api/image` 的其他方法 | 拒绝，返回 403 |

查询参数不改变路径匹配，所以 `/api/image?download=true` 同样公开。图片会展示日期、天气、安排、孕周、胎动及自定义文字等信息；免认证表示任何知道地址的人都可以查看。不要放行整个 `/api/`。

## 1. 准备域名、证书及应用

1. 将自己的域名解析至服务器。下文以 `board.example.com` 为占位域名，必须全部替换。
2. 通过现有证书工具或服务器管理面板取得有效的 HTTPS 证书，并配置自动续期。示例使用以下路径；证书位置不同则修改配置。

   ```text
   /etc/letsencrypt/live/board.example.com/fullchain.pem
   /etc/letsencrypt/live/board.example.com/privkey.pem
   ```

   配置中的 HTTP 入口只做跳转，没有配置 HTTP-01 验证目录；采用这种续期方式时，按证书工具要求配置验证目录，或使用其 Nginx 集成。证书未准备好时不要加载 HTTPS 配置。
3. 按项目 README 构建 JAR；服务器需要 Java 17 和中文字体。保留原数据目录，应用仍只监听本机。例如在原来启动应用的工作目录执行：

   ```sh
   SERVER_ADDRESS=127.0.0.1 SERVER_PORT=8080 java -jar /实际路径/kindle-home-dashboard-0.1.0.jar
   ```

   长期运行继续使用已有的进程管理方式。不要改变工作目录导致程序误用新的 `./data`，也不要同时启动两个进程写入同一数据目录。
4. 云安全组和服务器防火墙只开放需要的 80、443 及受限管理端口，**不要开放 8080**。本方案无需把 JSON 数据目录映射到 Nginx。

## 2. 创建账号密码文件

以下安装命令适用于 Debian / Ubuntu；其他发行版安装对应的 Nginx 和 `htpasswd` 工具即可。

```sh
sudo apt-get update
sudo apt-get install nginx apache2-utils
```

创建一个名为 `admin` 的账号。命令会交互式要求输入密码，不把密码写入命令历史或仓库：

```sh
sudo htpasswd -cB /etc/nginx/kindle-board.htpasswd admin
sudo chown root:www-data /etc/nginx/kindle-board.htpasswd
sudo chmod 640 /etc/nginx/kindle-board.htpasswd
```

`www-data` 是 Debian / Ubuntu 常见的 Nginx 工作进程组；如实际配置使用 `nginx` 等组，请相应替换。密码文件必须允许 Nginx 工作进程读取，不能放在站点静态目录中。

以后修改密码时**不要带 `-c`**，避免覆盖文件中的其他账号：

```sh
sudo htpasswd -B /etc/nginx/kindle-board.htpasswd admin
```

使用独立的长随机密码。浏览器会缓存 Basic Auth 凭据，没有应用内退出按钮；使用结束后关闭浏览器或使用独立的隐私窗口。

## 3. 安装配置

1. 备份服务器上已有的相关站点配置，将同目录的 `kindle-board.conf` 上传到 `/etc/nginx/conf.d/kindle-board.conf`。
2. 修改域名、证书路径，以及必要时的密码文件路径和应用端口。不要同时保留另一个代理到本应用的无认证站点。
3. 确认主配置的 `http` 块包含 `/etc/nginx/conf.d/*.conf`，然后执行：

   ```sh
   sudo nginx -t
   sudo systemctl reload nginx
   ```

   只有配置检查通过才重新加载。如果 Nginx 尚未启动，改为 `sudo systemctl start nginx`。不要覆盖整份 `nginx.conf` 或重启其他无关服务。

## 4. 验收

在可以访问公网域名的电脑或服务器上执行；以下 `curl` 是命令行工具，Windows PowerShell 使用 `curl.exe`。`-u admin` 会提示输入密码。

```sh
# 未提供账号：都应返回 401，并包含 WWW-Authenticate 响应头。
curl -I https://board.example.com/
curl -I https://board.example.com/api/state
curl -I https://board.example.com/api/settings
curl -I https://board.example.com/api/image/extra

# 提供正确密码：应返回 200；输入错误密码应返回 401。
curl -I -u admin https://board.example.com/
curl -I -u admin https://board.example.com/api/state

# 无账号拉图：应返回 200、Content-Type: image/png，得到实际 PNG。
curl --fail -D - -o dashboard-check.png https://board.example.com/api/image

# 图片接口不允许写操作：应返回 403。
curl -i -X POST https://board.example.com/api/image

# HTTP 应跳转至相同路径的 HTTPS 地址。
curl -I http://board.example.com/api/image
```

再用浏览器打开域名，确认出现账号密码框，登录后能保存设置、添加并删除一条测试安排，以及预览和下载图片。从另一台电脑确认公网 IP 的 8080 端口无法连接。

Kindle 拉图地址为 `https://你的域名/api/image`，无需账号密码。本配置只提供图片读取入口，没有实现设备端定时拉图、唤醒或显示脚本。必须在实际 Kindle 上验证 HTTPS 证书和 TLS 兼容性；不要用跳过证书验证作为长期方案。

## 5. 回滚与验证范围

回滚时先关闭该站点的公网入口或临时拒绝访问，再恢复备份的 Nginx 配置，执行 `nginx -t` 并重新加载。不要通过去掉认证来回滚。应用和数据均未改变，无需回滚数据库或 JSON。

已核对当前应用图片接口为 `GET /api/image`，配置仅对该精确路径关闭认证。当前开发环境未安装 Nginx，未执行真实的 `nginx -t`、公网认证请求或 Kindle 测试；部署时必须完成上面的验收。此配置未额外实现登录尝试限流。

依据：[Nginx Basic Auth](https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html)、[路径与方法限制](https://nginx.org/en/docs/http/ngx_http_core_module.html)、[反向代理](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)。
