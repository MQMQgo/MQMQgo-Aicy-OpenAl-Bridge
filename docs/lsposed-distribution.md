# LSPosed 模块仓库分发

**本项目是 AI 辅助氛围编程（Vibe Coding）的产物。** 当前发布仍为兼容修复候选，没有完成真实 Flyme / API102 的完整运行验证。

## 两种安装身份

| 分发 | 应用 ID | 用途 |
| --- | --- | --- |
| 原包名 | `io.github.aicybridge` | 使用原证书覆盖更新，保留既有配置和日志 |
| 官方仓库申请包 | `io.github.mqmqgo.aicybridge` | 对应维护者 GitHub 账号 MQMQgo，符合官方归属规则 |

包名变化会分配不同 Android UID，两个版本的配置、Keystore 和日志互不通用。新包不会读取或删除旧包的数据；首次使用需要重新输入渠道。请仅启用一个版本，避免重复 Hook。

## 官方收录流程

规则来源：[组织说明](https://github.com/Xposed-Modules-Repo/.github/blob/main/profile/README.md)、[提交说明](https://github.com/Xposed-Modules-Repo/submission/blob/master/README.md)。

1. 在 `Xposed-Modules-Repo/submission` 提交标题为 `[submission] io.github.mqmqgo.aicybridge` 的 Issue，由机器人建立专用分发仓库并邀请维护者。
2. 接受邀请，为分发仓库填写非空 Description（模块名称），设置支持链接，上传 README 和 SUMMARY。官方仓库只放分发说明与安装包，不上传源码。
3. 创建 Release，上传签名 APK 附件，标签为 `9-0.4.2`（`versionCode-versionName`），候选版标记为 prerelease。仅将 APK 放在源码仓库的 `dist/` 不满足官方收录要求。
4. 完成条件后等待官方索引更新；官方说明通常需要最多三个工作日。建立申请、获得分发仓库、Release 上传与网站可见是不同阶段，不以已提交代替已上架。

## 构建

```sh
# 原安装身份
./gradlew :app:assembleRelease

# 官方分发身份
./gradlew -PmoduleApplicationId=io.github.mqmqgo.aicybridge :app:assembleRelease
```

两个命令共用构建输出目录，执行下一次构建前需复制对应产物，并分别完成签名和身份检查。入口类和源码 namespace 保持 `io.github.aicybridge`；Provider 的 authority 随安装包 applicationId 改变。

源码仓库希望命名为 `Aicy-OpenAl-Bridge`；GitHub 仓库名用连字符代替空格。2026-11-15 当前连接尝试改名返回 HTTP 403（`Resource not accessible by integration`），因此尚未改名。当前连接未获 GitHub 仓库 Administration 写权限。


## 当前进度与需在 GitHub 页面完成的操作（2026-11-15）

- 两种身份的签名安装包、分发 README / SUMMARY / 申请正文已上传源码仓库，v0.4.2 候选 Release 已发布。
- 仓库改名：GitHub 接口返回 HTTP403，**实际名称仍是 `MQMQgo-Aicy-OpenAl-Bridge`**。
- 官方收录申请：创建 Issue 返回 HTTP403（`Resource not accessible by integration`），**没有创建申请，也没有获得官方分发仓库或上架**。
- Release 附件：上传返回 HTTP401，当前源码 Release 的附件列表为空；签名 APK 在源码仓库 `dist/` 中提供下载，不能将它算作已完成官方 Release 附件要求。

这些是 GitHub 连接权限限制，准备好的文件可在已登录的 GitHub 页面继续发布。

1. 打开[源码仓库设置](https://github.com/MQMQgo/MQMQgo-Aicy-OpenAl-Bridge/settings)，在 Repository name 中改为 `Aicy-OpenAl-Bridge`。GitHub 会保留旧 URL 重定向。
2. 点击[预填的官方收录申请](https://github.com/Xposed-Modules-Repo/submission/issues/new?title=%5Bsubmission%5D+io.github.mqmqgo.aicybridge&body=Module%3A+Aicy+OpenAl+Bridge%0AApplication+ID%3A+io.github.mqmqgo.aicybridge%0AMaintainer%3A+MQMQgo%0ASource%3A+https%3A%2F%2Fgithub.com%2FMQMQgo%2FMQMQgo-Aicy-OpenAl-Bridge%0A%0ADevelopment+disclosure%3A+This+project+is+a+product+of+AI-assisted+vibe+coding.+%E6%9C%AC%E9%A1%B9%E7%9B%AE%E6%98%AF+AI+%E8%BE%85%E5%8A%A9%E6%B0%9B%E5%9B%B4%E7%BC%96%E7%A8%8B%E7%9A%84%E4%BA%A7%E7%89%A9%E3%80%82%0A%0AStatus%3A+v0.4.2+%2F+versionCode+9%2C+compatibility+candidate+%28prerelease%29%3B+real-device+Flyme%2FAPI102+validation+and+confirmation+of+the+latest+startup-crash+fix+are+still+pending.%0A%0AFull+submission+and+signed+APK+link%3A+https%3A%2F%2Fgithub.com%2FMQMQgo%2FMQMQgo-Aicy-OpenAl-Bridge%2Fblob%2Fmain%2Fdistribution%2Flsposed%2FSUBMISSION.md%0A)，核对包名、来源声明和链接后提交。完整正文也在 [`SUBMISSION.md`](../distribution/lsposed/SUBMISSION.md)。
3. 机器人建仓后，在对应仓库 `/invitations` 页面接受维护邀请。填写 Description 为 `Aicy OpenAl Bridge`、支持链接为源码仓库；把 [`README.md`](../distribution/lsposed/README.md) 和 [`SUMMARY`](../distribution/lsposed/SUMMARY) 上传到分发仓库根目录。
4. 在分发仓库创建 `9-0.4.2` 候选 Release，把 [`aicy-openai-bridge-v0.4.2-lsposed-api102.apk`](../dist/aicy-openai-bridge-v0.4.2-lsposed-api102.apk?raw=true) 作为 APK 附件上传，并把 AI 氛围编程声明及真机验证范围保留在说明中。完成后再等待官方索引。
