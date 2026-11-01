# 更新现有 GitHub 仓库

可以继续使用原仓库：

[zhang33565417/KindleHomeDashboard](https://github.com/zhang33565417/KindleHomeDashboard)

这是本项目现有的 GitHub 仓库。后续维护先获取远端最新内容，再在原历史上正常提交和推送，无需新建仓库。

## 最终目录

```text
KindleHomeDashboard/             原来的仓库
  .git/                         保留 Git 管理目录，不上传、不覆盖、不删除
  README.md                     更新为新版首页说明
  pom.xml
  .gitignore
  .gitattributes
  src/
  deploy/
  docs/
  scripts/
  01-服务端jar包/                    当前可运行 JAR 和中文说明
  02-Kindle客户端脚本/              当前客户端、图块和中文说明
```

README 继续放在仓库根目录。旧代码目录按同名路径更新；不再另套“03-项目源码”或“GitHub上传目录”。

## 推荐：GitHub Desktop 更新

1. 在 GitHub Desktop 登录自己的 GitHub 账号，克隆原仓库到一个**新的空文件夹**。不要把已有未提交改动的开发目录当成覆盖目标。
2. 获取并拉取远端最新内容，确认使用仓库默认分支。若远端有本次本地整理后新增的代码，先比较和合并，不直接覆盖。
3. 将 `share/GitHub上传目录/` 中的**全部内容**复制到克隆目录根部，同名文件按新版更新；隐藏的 `.gitignore`、`.gitattributes` 也要复制。保留克隆目录原有的 `.git/`。
4. 在克隆目录中删除下文列出的四个旧版 `dist` 文件。只删除旧交付包，不删除自己的运行数据，也不清空其他不认识的文件。
5. 在 GitHub Desktop 的 Changes 中检查改动：README 和源码更新，两个交付文件夹新增，四个旧交付文件删除；不应出现 `data/`、密钥、日志或设备备份。
6. 提交，例如“完善 Kindle 安装教程并整理服务端与客户端”，再 Push。这样是在原仓库基础上增加一次正常提交，原来的网址和历史记录都保留。

克隆到新目录的目的是先获得远端当前内容，避免把本地尚未提交的其他改动混进去。如果远端新增过功能，应先合并这些改动再提交。

## 旧文件怎样处理

本地记录中原仓库跟踪了以下四个过期交付文件，应在这次更新中移除：

```text
dist/kindle-home-dashboard-0.1.0.jar
dist/kindle-home-dashboard-0.1.0.jar.sha256
dist/kindle-home-dashboard-0.1.0-updated.jar
dist/kindle-home-dashboard-0.1.0-updated.jar.sha256
```

新的可运行程序统一放在 `01-服务端jar包/kindle-home-dashboard.jar`，Kindle 文件统一放在 `02-Kindle客户端脚本/`。不需要新增 SHA256 文件。

只复制新文件不能移除 GitHub 上的旧文件，删除旧包也需要作为本次提交的一部分。`.gitignore` 不能自动删除已经跟踪的文件。

旧 `docs/` 中仍有价值的设计资料可以保留，当前安装以根 README 和 `deploy/kindle/README.md` 为准。私人维护记录和设备备份不提交。不要删除旧 Release 或改写 Git 历史来整理普通版本文件。

## 使用命令行时

以下命令适合今后继续更新仓库时使用。首次在新电脑维护时先克隆：

```sh
git clone https://github.com/zhang33565417/KindleHomeDashboard.git KindleHomeDashboard-public
cd KindleHomeDashboard-public
```

然后把整理目录内部内容复制到这里。检查远端当前文件无新增冲突后，删除指定旧交付文件：

```sh
git rm --ignore-unmatch -- dist/kindle-home-dashboard-0.1.0.jar dist/kindle-home-dashboard-0.1.0.jar.sha256 dist/kindle-home-dashboard-0.1.0-updated.jar dist/kindle-home-dashboard-0.1.0-updated.jar.sha256
git add .
git status --short
git diff --cached --stat
git commit -m "完善 Kindle 安装教程并整理服务端与客户端"
git push
```

不需要 `git init`，不需要重新设置 `origin`，不使用强制推送。仓库若有分支保护，按 GitHub 提示推到新分支并发起合并请求。

## README 和文件说明

- 根 `README.md`：首页介绍、示例图、统一文件说明、服务器启动、设备教程入口、日常使用与限制。
- `01-服务端jar包/文件说明.md`：JAR 怎么运行，数据在哪里，如何备份。
- `02-Kindle客户端脚本/文件说明.md`：设备文件分别做什么，哪些需要复制或配置。
- `deploy/kindle/README.md`：越狱适配、KUAL、Wi-Fi、USBNetwork/SSH、安装、排错和卸载。

源码和说明继续在原仓库中维护。可运行 JAR 不复制到 Kindle；不要把使用后生成的家庭数据同步回 GitHub。
