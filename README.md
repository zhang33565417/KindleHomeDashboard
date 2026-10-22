# Kindle Home Dashboard · V2 极简版

**分支说明：`lan` 用于本机及可信家庭局域网部署版本，该版本的后续维护在此分支进行。**

个人家庭看板：**Java 17 + Spring Boot 3 + 原生 HTML/CSS/JS + 本地 JSON + Java2D**。一个 JAR 同时提供管理页面、API 和灰度 PNG，无前端构建步骤。

**当前采用用户新提供的横版、竖版固定底图。** 孕妇、宝宝、山水、图标和卡片位置均为固定底图；程序只更新日期、农历、时间、天气、孕周、安排、胎动及指定文案，不再提供模块拖动、缩放或字号编辑。整张图片仍可按 Kindle 的输出像素等比缩放，不会改变模块比例。

> **无登录，只能在本机或可信家庭局域网运行，不可直接暴露公网。** 能访问服务的人都能查看和修改家庭数据。默认只监听 `127.0.0.1`。

公网版本在 `codex/internet` 分支准备了 [Nginx 部署配置与步骤](deploy/nginx/README.md)：应用保持原样，由 HTTPS 入口保护管理页面与接口，仅图片读取接口免认证（图片内容公开）。部署时按该文档限制应用端口，不能直接开放 8080。

## 启动

已有Java 17时，可直接下载本分支的[可执行JAR](dist/kindle-home-dashboard-0.1.0.jar)和[SHA256校验文件](dist/kindle-home-dashboard-0.1.0.jar.sha256)，不需要安装Maven。下载到独立文件夹后运行：

```powershell
java -jar kindle-home-dashboard-0.1.0.jar --server.address=0.0.0.0
```

仅在可信家庭局域网开放访问；数据自动保存在启动目录的 `data/`，不包含在JAR中。

要求 JDK **17**、Maven **3.6.3+**；首次构建需要联网下载依赖。请先用 `java -version`、`mvn -version` 确认两者使用 JDK 17（本机默认 Java 8 需要切换）。

```powershell
# 按本机安装位置调整；只影响当前 PowerShell 窗口
$env:JAVA_HOME = 'D:\Java\JDK\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;D:\Java\apache-maven-3.9.9\bin;$env:Path"
mvn spring-boot:run
```

打开 [http://127.0.0.1:8080](http://127.0.0.1:8080)。或打包后直接运行：

```powershell
mvn clean package
java -jar target/kindle-home-dashboard-0.1.0.jar
```

如果 Maven Central 连接超时，可在本机 Maven 设置中配置可用的镜像仓库，不属于项目运行依赖。

重新打包前请先停止本项目运行中的 JAR，避免 Windows 文件占用；完成打包后再启动。

运行 JAR 只需 Java 17 和中文字体，不需要 Maven、Node、数据库或浏览器内核。工程没有 MySQL、MyBatis、Flyway、Redis、登录、Docker 等组件。

## 使用

1. 首次启动自动在当前工作目录的 `./data` 创建设置、布局、安排、胎动、天气缓存及 `dashboard.png`。默认城市为演示用郑州，预产期为空。
2. “设置”中填写城市、经纬度、预产期、时区和图片宽高，再保存。默认 1072 × 1448 只是示例，**请按 Kindle 真实分辨率修改**，范围为 400–3000 像素。可在“图片方向”中选择横版或竖版，切换会自动交换宽高，保存后生效；也可直接修改宽高。宽大于高使用横版底图，否则使用竖版底图。竖版逻辑坐标为1080 × 1456，横版为1457 × 1079，输出按比例缩放，比例不同时保留白边。
3. “日常记录”支持按日期新增、编辑、完成、删除安排；PNG 只展示配置时区的今天。
4. 胎动先“开始计数”，再“记一次”；可撤销最近一次、结束、按日期查看历史及编辑备注。进行中的会话重启后可继续，跨午夜按每次点击的实际时间归属当天。仅作手动记录，不作医学判断。
5. “图中文字”编辑下次产检信息及底部寄语。新图去掉底部三条提醒卡片及其编辑入口；原提醒、便签和倒计时档案仍完整保留。
6. 固定模板展示最多四条今日安排、两段最近胎动会话。超出部分仍完整保存并可在管理页查看；长文字会在固定区域内缩小或省略。插画和模块边框不能移动，原布局中的坐标、大小和字号不再影响图片。
7. 右侧预览直接来自服务端 `GET /api/image`，不是 HTML 模板模拟图。下载得到同一张真实灰度 PNG。所有已保存内容即时重绘；服务按北京时间整点、半点生成新图及新版本，天气仍按设置间隔刷新。旧管理页的自动生成间隔字段保留兼容，不再控制后端调度。

天气由 [Open-Meteo](https://open-meteo.com/) 提供（[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)），包含气温、最高最低温、湿度和km/h风速；旧缓存没有风速时显示“—”，刷新成功后补齐。网络超时或数据缺失不会伪造数值。首次失败显示不可用；有缓存时保留原城市、原数值和上次成功时间。修改位置后可手动刷新天气，新位置请求失败时继续明确标注旧城市缓存。

底图来自用户参考图，使用内置 imagegen 一次性清除示例数据，分别保存于 `src/main/resources/templates/family-board-portrait.png` 和 `src/main/resources/templates/family-board-landscape.png` 并随JAR打包。运行时完全使用Java2D填字，不请求图片生成服务。农历采用 [lunar-java](https://github.com/6tail/lunar-java) 计算，不照抄参考图的静态日期或百分比。

## 配置与手机访问

| 环境变量 | 默认值 | 用途 |
|---|---|---|
| `SERVER_PORT` | `8080` | 端口 |
| `SERVER_ADDRESS` | `127.0.0.1` | 监听地址 |
| `BOARD_DATA_DIR` | `./data` | 数据和最新 PNG 目录 |
| `BOARD_FONT_PATH` | 空 | 可选中文 TTF/OTF 字体文件绝对路径 |

仅在可信家庭网络需要手机访问时：

```powershell
$env:SERVER_ADDRESS = '0.0.0.0'
$env:SERVER_PORT = '8080'
java -jar target/kindle-home-dashboard-0.1.0.jar
```

手机与电脑连接同一家庭网络，访问 `http://电脑局域网IP:8080`。防火墙只允许可信家庭网段；不要映射公网端口。跨设备访问无需账号密码。

## 字体和数据保护

- Windows 自动优先使用宋体以接近参考图；Linux 建议安装 `fonts-noto-cjk`（优先Noto Serif CJK）。也可用 `BOARD_FONT_PATH` 指定有授权的中文字体文件。没有中文字体时页面提示错误并保留旧图，不悄悄输出乱码。没有打包商用字体。
- 所有写入串行处理，JSON 和 PNG 先写临时文件再替换；文件系统支持时使用原子移动。图片失败保留旧图和旧成功时间，页面提示数据已保存但重绘失败。
- 无法解析的 JSON 会保留原文件、复制 `.corrupt-时间戳` 备份并拒绝启动；不要删除原文件来“修复”，应从备份恢复或人工修复格式。合法 JSON 的业务字段不合法也会拒绝启动并保留原文件。
- `data/` 和 `target/` 已加入 `.gitignore`。一个数据目录仅供一个服务进程使用；不支持多实例共同写入。
- 备份/恢复：**先停止服务**，复制/替换整个数据目录。目录位置相对启动时工作目录，与 JAR 所在位置不一定相同。不要把家庭数据公开或提交 Git。

## 接口

接口默认返回 JSON，输入错误返回 400，不存在记录返回 404。日期格式为 `YYYY-MM-DD`，时间以 ISO 8601 存储，展示和统计用设置时区。

| 方法 | 地址 | 内容 |
|---|---|---|
| GET | `/api/state` | 聚合设置、布局、孕周、天气、今日安排/胎动和生成状态 |
| GET / PUT | `/api/settings` | 读取/保存设置 |
| GET | `/api/layout` | 保留读取历史布局数据；PUT拒绝修改位置和尺寸 |
| GET / PUT | `/api/content` | 读取/保存五处固定文案；数据仍保存于layout.json |
| GET / POST | `/api/todos` | 按 `?date=` 查询 / 新增安排 |
| PUT / DELETE | `/api/todos/{id}` | 编辑（含完成）/ 删除 |
| GET | `/api/movements?date=YYYY-MM-DD` | 当日次数和相关会话；整段会话可能跨日 |
| POST | `/api/movements/start` | 新建或继续唯一的未结束会话 |
| POST | `/api/movements/{id}/tap`、`/undo`、`/end` | 记一次、撤销、结束 |
| PUT | `/api/movements/{id}/note` | 保存会话备注 |
| GET / POST | `/api/weather`、`/api/weather/refresh` | 读取缓存 / 手动刷新 |
| POST | `/api/render` | 重绘并返回聚合状态；检查 `renderError` |
| GET | `/api/image` | 最新成功 PNG；`?download=true` 下载 |
| GET | `/api/version` | 当前成功图片版本；只读、禁止缓存 |
| GET | `/api/health` | 进程健康检查 |

新增安排请求示例：

```json
{
  "date": "2026-10-21",
  "content": "阅读半小时",
  "done": false,
  "sort": 10
}
```

## 验证与范围

`mvn test` 运行少量关键闭环与失败保护测试。实际验收记录见 [TEST-RESULTS.md](TEST-RESULTS.md)，程序真实生成示例见 [docs/dashboard-example.png](docs/dashboard-example.png)及 [横版示例](docs/dashboard-landscape-example.png)（仅演示数据）。

Kindle 可每 30 秒请求 `GET /api/version`，成功返回 `200 application/json`：

```json
{
  "version": "1792627200000"
}
```

将版本作为不透明字符串比较，变化后请求 `GET /api/image?v=1792627200000`；需要附件下载时加 `&download=true`。图片接口返回 `200 image/png` 和完整 PNG 二进制；`v` 只用于避开缓存，无论其值是什么都返回最新成功图片，不选择历史版本，也不触发生成。两接口成功响应包含 `Cache-Control: no-store`、`Pragma: no-cache`、`Expires: 0`。查询后若恰有更新，下载可能取得更新的完整图片，下次轮询会再次发现版本变化。

每次成功生成均递增版本，失败保留旧图和旧版本。版本存于 PNG 文件修改时间，启动时完整解码有效图片并恢复版本；没有有效图片则尝试生成，仍失败时两接口返回 `404` 和含 `message` 的 JSON 错误。备份/迁移图片时应保留修改时间；数据目录应使用支持毫秒修改时间与同目录原子替换的本地文件系统，且只运行一个服务实例。回退部署可恢复原 JAR，业务 JSON 格式未改变。

本阶段**未实现 Kindle 客户端、KUAL/FBInk 脚本、自动推送、定时唤醒与休眠**。版本与图片接口为后续 Kindle 主动拉取提供支持，当前下载图片不代表 Kindle 已能自动更新。也未实现图片上传、医学预警、多用户、多实例或复杂历史报表。
