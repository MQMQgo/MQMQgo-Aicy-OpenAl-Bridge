# Android 设置应用与配置桥

模块应用 ID 为 `io.github.aicybridge`，设置界面为 Kotlin/Compose `SettingsActivity`，使用 Miuix 0.9.4，跟随系统浅色与深色模式。`BridgeApp` 使用参考 Miuix 玻璃示例的 `GlassCapsuleNavigation` 在主页和日志页间切换，两页保留独立滚动状态；API 草稿由现有 ViewModel 保留，密钥不写入保存状态。底栏叠放在页面上方，滚动容器末尾预留空间，移除整块底栏占位区域。材质和平台回退见 [玻璃底栏](../docs/glass-navigation.md)。

## 使用

1. 安装模块，在支持现代 libxposed API 102 的 LSPosed 中启用，作用域选择 Aicy 助手。
2. 打开模块设置，填写 HTTPS API 前缀（通常包含 `/v1`，也可填写完整 `/chat/completions` 地址）和 API Key；模型 ID 可手填，也可点击“获取模型”从列表搜索选择。
3. 可点“测试连接”。仅在主动点击时向填写的服务商发送一句“请只回复 OK。”；该操作可能产生少量 API 费用，不会改变已保存配置。连接测试验证普通 Chat Completions 消息，不证明该服务商支持流式输出、图片或工具调用。
4. 点击“保存并启用”。已有渠道可点“编辑渠道”再“保存修改”，保存修改保留原来的开关状态；密钥留空会保留原密钥，更换 API 主机时需明确填写密钥。**保存后必须在系统应用设置中强行停止 Aicy，再重新打开；本版配置在目标进程启动时读取，不热加载。** 关闭接管后同样要重启 Aicy。

本地保存前会检查 HTTPS 地址、模型非空和密钥换行。保存不进行网络请求；模型是否实际存在、额度和访问权限只能由服务商验证。错误信息仅显示固定描述或 HTTP 状态码，不显示服务商原始错误、密钥或消息正文。

模型 ID 下的“获取模型”使用 HTTPS GET 读取服务商 `/models`，无需先填写模型。Miuix 对话框支持搜索、选择和关闭；选择只更新编辑草稿，保存后才生效。URL 或 Key 改变会取消获取并失效旧列表。密钥留空仅在 API 域名和端口未改变时沿用已存密钥。详见 [模型列表](../docs/model-catalogue.md)。

## 私有存储

调用日志从底部胶囊的日志图标进入，在同一 Activity 中显示独立 `CallLogsScreen`，使用独立私有 SQLite 数据库。只有日志页可见且 Activity 在前台时轮询，切回主页或退到后台即停止。记录范围、HTTP 阶段定义、开关、保留上限与导出隐私见 [调用日志](../docs/call-logs.md)。

`ConfigStore` 使用模块私有 SharedPreferences，保留 v0.1.0 的 `settings` 文件、`aicybridge.api-key.v1` Keystore 别名与密文格式。API Key 以 Android Keystore 中 AES-256/GCM 密钥加密，随机 IV 与密文保存于私有偏好，密钥不导出。备份和设备迁移规则均排除应用数据。

保存使用同步 `commit()` 并确认返回值，跨实例写入共用锁；保存标记与开关状态独立。旧版没有保存标记时，根据原地址、模型字段恢复。退出重开会读取已提交记录；停用只写开关，不清空渠道。解密失败时接管保持关闭，但界面仍显示地址、模型和密钥保存状态，允许重试；读取异常不回写空记录。留空保留密钥时沿用原密文，避免临时读取异常覆盖凭据。ViewModel 保留旋转时的编辑内容，密钥不写入 Activity Bundle。

开关关闭时，Provider 不返回已保存的 API Key。启用后，获准的 Aicy 进程必须拿到明文 Key 才能直接请求服务商；本模块不能让目标进程在使用密钥的同时无法读取它。

## Provider 接口

Authority：`io.github.aicybridge.settings`，URI：`content://io.github.aicybridge.settings`。

只允许模块自身 UID，或同时满足以下条件的目标调用：

- `getCallingPackage()` 为 `BuildConfig.TARGET_PACKAGE`；
- Binder 调用 UID 的已安装包列表包含该目标包；
- 目标当前唯一签名的 SHA-256 与 `BuildConfig.TARGET_CERT_SHA256` 相同。

空的目标标识、签名不匹配或校验异常会拒绝访问。目标为从用户提供 APK 验证的 `com.meizu.voiceassistant`；证书固定值位于 `app/build.gradle`。不使用 world-readable preferences，也不通过 `query`、`insert`、`update`、`delete` 暴露数据。

`ContentResolver.call(URI, "get_config", null, null)` 返回 Bundle：

| Key | 类型 | 含义 |
| --- | --- | --- |
| `enabled` | boolean | 已保存的启用状态；密钥解密失败时为 false |
| `base_url` | String | HTTPS API 前缀或完整 Chat Completions 地址 |
| `api_key` | String | 启用时的明文密钥，否则为空 |
| `model` | String | 服务商模型 ID |

`ContentResolver.call(URI, "report_status", null, extras)` 接收 `extras.getString("status")`，仅接受以下固定代码：`waiting`、`hook_ready`、`unsupported_version`、`hook_error`、`bridge_enabled`、`bridge_disabled`、`request_started`、`request_completed`、`request_failed`、`cancelled`。返回 Bundle 的 `ok` 表示状态是否保存。禁止将 Key、用户消息、工具参数或异常原文写入状态。

设置界面显示最近一次报告及其时间。旧状态不代表目标进程仍在运行，状态也不能替代真机功能验证。

## 构建

使用 JDK21、Gradle9.7.1、AGP9.5.1、Kotlin/Compose 插件2.4.21、官方 `platforms;android-37.0` 与 `build-tools;37.0.0`。`compileSdk 37` 同时设置 `compileSdkMinor 0`。目标 SDK35，最低 Android8/API26。API102 使用官方 Maven Central AAR `io.github.libxposed:api:102.0.0` 作为 `compileOnly`，不把 API 实现打入 APK。

官方 API102 AAR 声明 `minCompileSdk=37`，Miuix 与 AndroidX Compose 1.12 要求新版 AGP，因此同步升级构建链；依赖元数据检查保持启用。为显式使用 Kotlin 2.4.21，`gradle.properties` 中关闭 AGP 内置 Kotlin，使用兼容的旧 DSL；AGP 会输出后续版本迁移提醒。

当前已在真实 SDK37.0 上通过完整调试/发布构建和 lint，包括 Manifest、XML、API102 AAR 元数据、Java 和 DEX 检查。签名及打包结果见根目录 [VALIDATION.md](../VALIDATION.md)。没有连接用户真机，因此不声称 Keystore、跨进程验签或 Hook 已完成设备验证。
