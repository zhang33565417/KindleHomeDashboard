# 接口速查

本项目无内置认证，仅在本机或可信家庭局域网使用，不直接映射到公网。

| 方法 | 地址 | 用途 |
| --- | --- | --- |
| GET | `/api/health` | 进程健康检查 |
| GET | `/api/state` | 设置、天气、今日记录与渲染状态 |
| GET / PUT | `/api/settings` | 设置 |
| GET | `/api/layout` | 历史布局读取；不支持修改位置与尺寸 |
| GET / PUT | `/api/content` | 文案 |
| GET / POST | `/api/todos` | 按 `?date=YYYY-MM-DD` 查询/新增安排 |
| PUT / DELETE | `/api/todos/{id}` | 修改/删除安排 |
| GET | `/api/movements?date=YYYY-MM-DD` | 当日记录 |
| POST | `/api/movements/start` | 开始或继续计数 |
| POST | `/api/movements/{id}/tap`、`/undo`、`/end` | 记一次、撤销、结束 |
| PUT | `/api/movements/{id}/note` | 保存备注 |
| GET / POST | `/api/weather`、`/api/weather/refresh` | 缓存/刷新天气 |
| POST | `/api/render` | 重绘，检查返回的 `renderError` |
| GET | `/api/image` | 灰度 PNG，`?download=true` 下载 |
| GET | `/api/version` | `version`、可选 `kindleVersion` 和版式信息 |
| GET | `/api/kindle/frame` | 清单 + PNG + 局部背景 ZIP |

客户端将版本视为不透明字符串比较。`kindleVersion` 对应去除服务端时钟后的内容版本，与 `/api/kindle/frame?contentOnly=true` 配套使用，减少时钟引发的整图下载。`/api/image?v=...` 中 `v` 仅避缓存，不查询历史版本。下载失败或版式不兼容时保留旧图。

日期用 `YYYY-MM-DD`，时间以 ISO 8601 存储、按设置时区展示。错误输入通常返回 400，记录不存在返回 404。最终行为以 `BoardController.java`、`BoardService.java`、`KindleFrame.java` 为准。
