# 安装包下载

**本项目是 AI 辅助氛围编程（Vibe Coding）的产物。** 当前 v0.4.2 是分发身份调整版，保留 v0.4.1 的兼容修复候选状态，尚未确认真实 Flyme / API102 上的完整运行与闪退恢复。

| 安装包 | 包名 | 用途 |
| --- | --- | --- |
| [原包名覆盖更新包](aicy-openai-bridge-v0.4.2-api102.apk?raw=true) | `io.github.aicybridge` | 已有用户覆盖安装，保留配置及日志 |
| [LSPosed 官方仓库申请包](aicy-openai-bridge-v0.4.2-lsposed-api102.apk?raw=true) | `io.github.mqmqgo.aicybridge` | 符合维护者命名空间归属规则的新安装身份 |

**两个包名的数据和 Keystore 互不通用。新包首次安装需重新填写渠道；请仅启用一个版本。** 两个包都使用原发布证书签名。原包名更新不要先卸载或清除数据。

版本 `0.4.2`（9），minSdk26 / targetSdk35，要求现代 libxposed API102，适配 Aicy 13.1.1（13001001）。保存配置后强行停止并重开 Aicy。源代码对应标签 `v0.4.2`。

- `aicy-openai-bridge-v0.4.2-api102.apk`：7,517,943 字节；SHA-256：`e9568984e12887ff968c0e3314702bfb52ac9515819caa83372937ad8449364e`。
- `aicy-openai-bridge-v0.4.2-lsposed-api102.apk`：7,517,943 字节；SHA-256：`f12be9f014d89bc63aa9cdc3750093ebe42f92aec5204b1cef8c471cc704895d`。
- 共同发布证书 SHA-256：`85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf`。

两种身份均完成应用测试，含完整 HTTP200 日志上报链路；协议层测试沿用同一 core。测试汇总为 69 项，无失败、错误或跳过。调试/发布构建及 lint 通过（0 错误、2 警告），签名、对齐、API102 打包与每个包的 Manifest / 运行时 Provider 地址核对通过。

安装包先发布在源码仓库，官方仓库建立、填充说明、上传 Release APK 附件和网站索引仍需分别完成。进度见 [分发说明](../docs/lsposed-distribution.md)。历史安装包及校验值继续保留。

当前源码 Release 附件上传返回 HTTP401，请使用本页 `dist/` 下载链接。官方收录申请返回 HTTP403，尚未创建申请或上架。
