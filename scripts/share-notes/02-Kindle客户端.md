# Kindle 客户端文件说明

将本文件夹中的 **`kindle-dashboard` 整个目录**复制到 Kindle USB 存储根目录的 `extensions/`，最终为 `/mnt/us/extensions/kindle-dashboard/`。本说明留在电脑上即可。

仅针对当前已适配的 PW4（1072 × 1448）。安装前先阅读 `kindle-dashboard/README.md`，里面包含越狱、KUAL、Wi-Fi、USBNetwork/SSH、显示工具安装与排错。这里不包含越狱工具、FBInk 和 evtest，需要另装。

## kindle-dashboard 里面的文件

| 文件/目录 | 作用 |
| --- | --- |
| `README.md` | 完整设备安装与联网教程。 |
| `HYBRID_POWER.md` | 当前白天/夜间/插电省电策略。 |
| `config.xml`、`menu.json` | 让 KUAL 显示“家庭信息看板”菜单。 |
| `server-url.example.txt` | 服务器地址示例。复制为 `server-url.txt`，填运行服务端的电脑 IP 和端口。 |
| `power.conf` | 联网周期、夜间时段、手动唤醒宽限时间等配置。 |
| `layout-assets/portrait-v1/` | 竖版时钟、休眠时钟、电量图块，共 3082 张 PNG。无需逐张打开或修改。 |
| `layout-assets/landscape-v1/` | 横版对应图块，共 3082 张 PNG。 |
| `check-install.sh` | 启动前检查工具、硬件节点、图块和地址配置；不进入看板。 |
| `control.sh` | 进入/退出看板的主控制脚本，负责切换原生界面和后台任务。 |
| `common.sh` | 目录、日志、进程与锁等公共函数。 |
| `layout-common.sh` | 读取服务地址、校验布局清单、加载缓存和触摸区域。 |
| `dashboard.sh`、`download-frame.sh` | 下载并校验服务器的完整画面包，失败时保留旧画面。 |
| `display.sh`、`render-frame.sh` | 使用 FBInk 把画面显示在屏幕上。 |
| `clock.sh` | 按当前模式叠加本地时钟。 |
| `status.sh` | 叠加电量、充电状态。 |
| `power-loop.sh` | 调度联网、时钟更新和休眠。 |
| `power-common.sh`、`power-policy.sh` | 电源状态、前光恢复和模式计算。 |
| `power-events.sh` | 观察电源键，提供短暂交互时间。 |
| `sleep-cycle.sh` | 设置 RTC 唤醒并执行系统 RAM 休眠。 |
| `touch-loop.sh`、`long-press.awk` | 识别点房子操作，过滤拖动/长按等，返回原生主页。 |
| `back-to-dashboard.sh`、`start-dashboard.sh` | 进入看板的快捷入口。 |
| `refresh-dashboard.sh` | 手动请求刷新。 |
| `restore-ui.sh` | 退出看板、恢复原生主页。 |
| `start-auto-refresh.sh`、`stop-auto-refresh.sh` | 菜单使用的启动/停止入口。 |
| `boot-start.sh` | 供已有开机集成调用；本次没有安装系统自启。 |
| `auto-refresh.sh`、`status-loop.sh`、`calendar.sh` | 较早实现保留的兼容脚本，不是当前正常安装的手动操作入口。 |
| `test-*.sh`、`rtc-smoke-test.sh` | 开发验证脚本，普通安装不用运行；RTC 测试会涉及休眠。 |
| `bin/fbink`（需要自行补齐） | 与设备匹配的屏幕绘制工具，文件夹版没有捆绑第三方二进制。 |
| `frames/`、`frame-current`、`*.log`（使用后生成） | 下载缓存、当前画面指针、运行日志，可能包含家庭内容，不公开上传。 |

不要逐个执行所有 `.sh`。正常使用通过 KUAL 菜单；需要 SSH 操作时，按 README 使用 `check-install.sh` 和 `control.sh` 即可。
