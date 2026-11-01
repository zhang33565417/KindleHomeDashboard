# Kindle 安装、越狱与联网指南

适用范围：本项目客户端以 **Kindle Paperwhite 4（PW4），1072 × 1448** 为目标，使用 PW4 的触摸设备、电源节点与 RTC。其他 Kindle 即使越狱成功，也不代表能直接使用这些脚本。下列命令中服务器 IP、Kindle IP 都是示例，需要替换。

## 1. 确认型号与越狱

1. 充好电，备份书籍和个人文件。在“设置 → 设备选项 → 设备信息”记录机型和完整固件版本；型号不明确时，使用社区设备识别指南核对，别只看屏幕大小。
2. 暂时开启飞行模式，先阅读 [KindleModding 越狱说明](https://kindlemodding.org/jailbreaking/)，再进入 [型号/固件适配向导](https://kindlemodding.org/jailbreak-wizard.html)。
3. 输入自己的信息，按向导给出的方法下载文件、执行操作。需要联网、注册账号或连接 USB 时，以该方法的当前教程为准；不把其他型号教程中的固件刷到本机。
4. 按该教程完成要求的 Hotfix、更新防护及后续配置。不同方法可能自动安装部分工具；已安装的工具先验证，不重复覆盖。
5. 确认能运行自制软件后，再继续下面步骤。

越狱方案会变，不能把某个方法当成所有固件都适用。没有匹配方案时先停在这里，不为了越狱盲目升级或尝试未越狱降级。恢复出厂设置也不是本项目的安装步骤。具体兼容性始终以社区向导为准。

## 2. 安装 KUAL 和 MRPI

KUAL 是启动自制程序的菜单，MRPI 用于安装兼容的更新包。按越狱后的当前指引安装；传统流程见 [KUAL/MRPI 指南](https://kindlemodding.org/jailbreaking/Legacy/post-jailbreak/installing-kual-mrpi/)。

以当前页面列出的 PW4 所属 K5 及更新设备流程为例：下载对应 MRPI，将解压后的 `extensions`、`mrpackages` 合并复制到 Kindle USB 存储根目录；若选择 PEKI 方式，把其 `KUAL.sh`、`KUAL.jar` 放进 `documents/`。安全弹出并拔线，打开书库中的 KUAL。若向导提供其他安装方式，跟随该方式，不混装不对应的版本。

电脑看到的 Kindle 盘符根目录，在 SSH 中对应 `/mnt/us/`。例如 `Kindle盘符:\extensions` 就是 `/mnt/us/extensions`。合并目录时不要删除已有扩展。

验收：打开 KUAL 能看到扩展菜单，不出现应用错误。空间不足、菜单打不开时先按上游排错，再装本项目。

## 3. 连接 Wi-Fi 与 SSH

### Wi-Fi 和 USBNetwork 是两件事

- **Wi-Fi**：Kindle 从家庭网络下载看板。先在 Kindle 系统网络设置中连接家用 Wi-Fi；不识别网络时可先尝试 2.4 GHz、兼容的 WPA2 网络，避免网页认证及访客隔离。
- **USBNetwork / USBNetLite**：越狱后的 SSH 工具，可以把 USB 变成网络连接，也可按其配置允许 Wi-Fi SSH。仅打开 Wi-Fi 不会自动获得 SSH。

服务器、手机、Kindle 放在同一家庭网络；服务器需要访问天气服务。首次联网前完成越狱教程要求的更新防护；飞行模式会断开 Wi-Fi，不能在飞行模式下持续拉取看板。

### 选择并安装 SSH 工具

下载和适配说明：

- [NiLuJe 工具包及 USBNetwork](https://www.mobileread.com/forums/showthread.php?t=225030)
- [USBNetwork 使用说明](https://wiki.mobileread.com/wiki/USBNetwork)
- [USBNetLite 项目与安装说明](https://github.com/notmarek/kindle-usbnetlite)

按自己的固件、架构和上游说明选择一种，不同时安装互相冲突的工具。本项目原客户端依赖传统路径 `/mnt/us/usbnet/bin/evtest`；USBNetLite 只解决 SSH，并不保证提供 FBInk、evtest 等显示工具。

对要求 MRPI 的安装包：把上游指定的 `.bin` 放在 `mrpackages/`，安全弹出并拔线，再在 Kindle 搜索栏运行 `;log mrpi`。不要把本项目的 `.tar.gz` 放进 MRPI，也不要通过“更新 Kindle”安装它。安装后在 KUAL 中检查 USB 网络菜单。

### Windows 通过 USB Network 连接

1. 拔线，按所选工具说明在 KUAL 启用 USB Network（有些版本支持搜索 `;un` 切换）。
2. 接回电脑。此时 Kindle 可能不再显示为 U 盘，而显示为 USB 以太网/RNDIS 网卡，这是网络模式的正常表现。
3. 如果没有网卡，按上游说明安装适合设备的 RNDIS 驱动。在 Windows 网络适配器设置中仅修改 **Kindle 对应网卡**。
4. 从安装包说明或设备配置读取 Kindle 的 USB IP 和电脑端 IP，设置同一子网、不同地址，通常不填网关和 DNS。不同包默认地址不同，不照抄别人的地址。
5. 例如你已确认 Kindle 为 `192.168.15.244`、电脑为 `192.168.15.201/24`，在 PowerShell 执行：

```powershell
ssh root@192.168.15.244
```

上面只演示命令格式，不是通用默认地址。首次连接核对设备主机密钥指纹，认证使用该工具配置的密码或密钥。没有统一的通用密码。建议配置 SSH 密钥后再开启 Wi-Fi SSH，私钥只留电脑。

回到文件复制模式前，按工具说明关闭 USB Network、安全拔插。不要反复安装驱动来试图让网络模式同时显示为 U 盘。

### 通过 Wi-Fi SSH 连接

1. 按上游说明编辑对应配置。传统 USBNetwork 常在 `/mnt/us/usbnet/etc/config`，USBNetLite 常在 `/mnt/us/usbnetlite/etc/config`；是否支持、如何启用 `USE_WIFI` 以实际配置注释为准。
2. 按工具菜单重启 SSH 服务。从路由器设备列表查看 Kindle 的 Wi-Fi IP，部分固件可在搜索栏输入 `;711` 查看网络信息。
3. 电脑执行 `ssh root@Kindle的Wi-Fi-IP`。不要把 USB IP 当 Wi-Fi IP，也不要把 Kindle IP 填到看板的服务器配置中。
4. 在 SSH 中先执行 `uname -a`、`ls /mnt/us`，确认连接到的是自己的 Kindle。

SSH 仅在可信家庭网络使用，不转发公网 22 端口。电池休眠期间 SSH 可能断开，安装时保持插电和唤醒。

## 4. 安装本项目客户端

### 文件准备

打开分享目录中的 `02-Kindle客户端`，里面的 `kindle-dashboard` 已包含脚本、KUAL 配置和两套图块，不需要解压。它不包含越狱工具、FBInk 或 USBNetwork。先阅读同目录的 `文件说明.md` 了解各文件用途。

服务端先按根 README 启动，手机能打开管理页。客户端目录应为：

```text
kindle-dashboard/
  config.xml
  menu.json
  control.sh、power-loop.sh 等脚本
  power.conf
  server-url.example.txt
  layout-assets/
    portrait-v1/     3082 个 PNG
    landscape-v1/    3082 个 PNG
```

把整个 `kindle-dashboard` 文件夹复制到 Kindle 的 `extensions/`。最终路径必须是 `/mnt/us/extensions/kindle-dashboard/control.sh`，不要多嵌套一层同名目录。已有安装先退出、备份，再合并覆盖，保留自己的配置和二进制。

### 填写服务器地址

把 `server-url.example.txt` 复制为同目录下的 **`server-url.txt`**，仅写一行，例如：

```text
http://192.168.1.100:8080
```

这是运行 JAR 的电脑 IP 和端口；不带 `/api/image`、`/api/version` 或末尾斜杠，不加引号。不要将服务器密码写在 URL 中。本项目没有默认公共服务器。公网 HTTPS 使用自己的域名并按 Nginx 指南部署；旧 Kindle 的证书/TLS 要实测，不用跳过证书检查代替修复。

### 补齐 FBInk 和 evtest

按 [FBInk 上游](https://github.com/NiLuJe/FBInk) 和 NiLuJe 工具包获取匹配 PW4 架构、支持 PNG 的程序。若传统 USBNetwork 已提供，可在 Kindle SSH 中检查并复制：

```sh
mkdir -p /mnt/us/extensions/kindle-dashboard/bin
ls -l /mnt/us/usbnet/bin/fbink /mnt/us/usbnet/bin/evtest
# 仅在上述文件确实存在且适配本机时执行：
cp /mnt/us/usbnet/bin/fbink /mnt/us/extensions/kindle-dashboard/bin/fbink
chmod +x /mnt/us/extensions/kindle-dashboard/bin/fbink
/mnt/us/extensions/kindle-dashboard/bin/fbink --help
```

触摸返回和电源键观察使用 `/mnt/us/usbnet/bin/evtest`。如果用 USBNetLite 且没有该文件，需要另行从可信上游安装兼容 evtest 至该路径；不能把 SSH 可用当作依赖齐全。rtcwake、flock、unzip 等也必须可用。不要从其他 CPU 架构的包里随便复制程序。

### 从源码生成图块（客户端文件夹已含图块可跳过）

在电脑项目根目录运行，Python 只在电脑使用。字体路径按实际环境调整，字体需能显示中文：

```powershell
python -m pip install Pillow
python scripts/make-client-assets.py --font C:/Windows/Fonts/simsun.ttc --bold-font C:/Windows/Fonts/timesbd.ttf
```

Linux 可使用自己安装的 Noto CJK 和衬线字体路径。输出在 `deploy/kindle/layout-assets/`，与脚本一起复制。旧 `make-*.py` 用于历史资源维护，新安装直接使用 `scripts/make-client-assets.py`，无需生成旧农历表。

## 5. 检查并启动

先在 Kindle SSH 中只读检查：

```sh
sh /mnt/us/extensions/kindle-dashboard/check-install.sh
wget -O /tmp/board-version.json http://192.168.1.100:8080/api/version
cat /tmp/board-version.json
wget -O /tmp/board-frame.zip http://192.168.1.100:8080/api/kindle/frame
unzip -l /tmp/board-frame.zip
```

替换成自己的服务器地址。应返回 JSON 和包含 `manifest.txt`、`frame.png`、`clock-background.png`、`battery-background.png` 的 ZIP。出现 HTML、401、连接失败或依赖缺失时，先修复，不进入看板。只读检查通过仍不等于其他机型兼容。

安装后在 KUAL 选择“家庭信息看板 → 返回家庭看板”，或通过 SSH 执行：

```sh
sh /mnt/us/extensions/kindle-dashboard/control.sh dashboard
```

首次启动需联网下载；之后可先显示缓存。此操作会暂时停止原生界面绘制并接管触摸，退出脚本会恢复。请先保持 SSH 可用以便返回。

验收：

1. 画面、时钟、电量可见；修改一条无隐私的测试安排后等待更新，插电时约 30 秒检查一次。
2. 管理页切换 1072 × 1448 / 1448 × 1072，确认下一次更新后图像和房子位置一起切换。
3. 点房子返回原生主页，可以正常打开书籍；再从 KUAL 返回看板。
4. 拔电后验证定时休眠和电源键唤醒；睡眠时要先按电源键，再点房子。不要为了触摸测试直接把物理电源键长按成强制重启。

当前整理不默认安装开机自启，也不修改根文件系统。仓库的 `boot-start.sh` 仅供已有启动集成调用，新用户先用 KUAL 手动启动；历史 PW4 的 Upstart 安装不可当成通用新机步骤。

## 6. 排错、升级与卸载

通过 SSH 强制正常退出：

```sh
sh /mnt/us/extensions/kindle-dashboard/control.sh home
```

常用检查：

```sh
cat /tmp/kindle-dashboard/profile
cat /mnt/us/extensions/kindle-dashboard/server-url.txt
tail -n 50 /mnt/us/extensions/kindle-dashboard/controls.log
tail -n 50 /mnt/us/extensions/kindle-dashboard/dashboard.log
tail -n 50 /mnt/us/extensions/kindle-dashboard/touch-loop.log
```

- 找不到菜单：确认 `config.xml`、`menu.json` 位于正确层级，重新打开 KUAL。
- `^M`、`not found`：Shell 文件必须为 LF 换行；从客户端文件夹重新复制，避免编辑器转成 CRLF。
- `Exec format error`：二进制架构不符；换匹配 PW4 的上游程序。
- 无法联网：区分 Kindle IP 与服务器 IP，检查 Wi-Fi、服务器监听和防火墙；电池模式先插电唤醒。
- 旧画面不更新：检查日志、服务器新接口和版本；失败时保留旧画面是预期保护。
- 点房子无效：先唤醒，再检查 evtest、goodix-ts 触摸节点与固件；不要移植本机触摸坐标来适配其他型号。

升级：退出看板，备份整个扩展文件夹，再覆盖新文件；保留 `server-url.txt`、`bin/`，按需保留有效 `frames/` 缓存。服务端升级见根 README。不要在工作进程运行期间覆盖脚本。

卸载：先执行 `control.sh home`，确认原生主页恢复；如曾自行接入自启，先停用那项集成。然后通过 USB 将扩展目录移出 `extensions/` 备份，重开 KUAL 确认菜单消失。无需删除书籍、恢复出厂设置或卸载越狱工具。

## 参考来源

以上越狱与第三方工具步骤于 2026-11-01 核对。社区页面可能迁移或更新，适配向导及各包内 README 优先；本项目不镜像第三方下载，也不承诺所有固件可越狱。

- [KindleModding 入门](https://kindlemodding.org/jailbreaking/) 与 [适配向导](https://kindlemodding.org/jailbreak-wizard.html)
- [KUAL/MRPI](https://kindlemodding.org/jailbreaking/Legacy/post-jailbreak/installing-kual-mrpi/)
- [NiLuJe 工具包](https://www.mobileread.com/forums/showthread.php?t=225030)
- [USBNetwork](https://wiki.mobileread.com/wiki/USBNetwork)、[USBNetLite](https://github.com/notmarek/kindle-usbnetlite)
- [FBInk 源码与许可](https://github.com/NiLuJe/FBInk)
