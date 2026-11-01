# 分享与更新 GitHub

## 使用现有仓库

沿用 [zhang33565417/KindleHomeDashboard](https://github.com/zhang33565417/KindleHomeDashboard)，无需新建仓库或重新初始化 Git。

**具体操作见 [更新现有仓库](EXISTING_REPOSITORY.md)**：先获取远端最新内容，将整理目录内部文件更新到仓库根目录，保留 `.git/`，删除指定旧交付包，再正常提交、推送。整理脚本只准备文件，不会自动提交或推送。

## 已整理好的目录

`share/GitHub上传目录/` 内部就是仓库根目录：README、pom.xml、src、docs、scripts，以及 `01-服务端jar包`、`02-Kindle客户端脚本`。

上传目录内部内容，不套外面的“GitHub上传目录”这一层。README 已位于根目录，上传后显示在首页。各目录都有中文文件说明，没有压缩包或 SHA256 校验文件。

## 重新生成

在项目根目录运行，需要 Java 17、Maven 3.6.3+、Python 3 和 Pillow：

```sh
mvn clean package
python -m pip install Pillow
```

Windows 生成图块并整理文件（其他系统替换为自己的中文字体和数字字体路径）：

```powershell
python scripts/make-client-assets.py --font C:/Windows/Fonts/simsun.ttc --bold-font C:/Windows/Fonts/timesbd.ttf
python scripts/prepare-share.py
```

脚本会重建 `share/GitHub上传目录/`。不要在生成目录中保留自己的修改或运行数据，先复制到另一处再实际使用。

## 发布范围

第三方越狱包、USBNetwork/USBNetLite、FBInk、evtest 不随目录分发，按设备教程从上游安装。当前客户端针对 PW4，新机全流程尚未在第二台设备验证。正式选择开源许可证前需确认代码和底图素材授权，本次没有擅自添加许可证。
