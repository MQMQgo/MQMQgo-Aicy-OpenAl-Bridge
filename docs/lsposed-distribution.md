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

源码仓库希望命名为 `Aicy-OpenAl-Bridge`；GitHub 仓库名用连字符代替空格。2026-11-15 当前连接尝试改名返回 HTTP 403（`Resource not accessible by integration`），因此尚未改名。该权限问题不等于用户未授权。
