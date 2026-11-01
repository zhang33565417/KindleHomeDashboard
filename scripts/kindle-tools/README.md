# Kindle 开发工具

当前客户端仅维护在根目录 `02-Kindle客户端脚本/kindle-dashboard/`，普通使用不需要运行这里的工具。

本目录保留早期资源维护工具，避免混进需要复制到设备的文件夹：

| 文件 | 用途 |
| --- | --- |
| `LunarTable.java` | 生成旧版离线农历标签和日期表。 |
| `RenderBackground.java` | 按旧渲染方式生成背景参考图。 |
| `make-background-assets.py` | 从背景图裁剪旧版局部恢复底图。 |
| `make-clock-assets.py` | 生成旧版时钟、日期和农历字块。 |
| `make-layout-assets.py` | 将旧版透明资源转换成横竖版两套图块。 |
| `make-sleep-clock-assets.py` | 生成带“休眠时刻”的时钟图块。 |
| `make-status-assets.py` | 生成电量和充电状态图块。 |
| `test-long-press.py` | 生成触摸事件回放测试数据。 |

当前完整图块直接由 `scripts/make-client-assets.py` 生成，默认写入客户端的 `layout-assets/`；不需要按顺序执行上面这些旧工具。
