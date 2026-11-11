# Android 设置应用与配置桥

模块应用 ID 为 `io.github.aicybridge`，设置界面为 `SettingsActivity`。使用平台控件，无界面框架依赖。

## 使用

1. 安装模块，在支持现代 libxposed API 102 的 LSPosed 中启用，作用域选择 Aicy 助手。
2. 打开模块设置，填写 HTTPS API 前缀（通常包含 `/v1`，也可填写完整 `/chat/completions` 地址）、API Key 和模型 ID。
3. 可点“测试连接”。仅在主动点击时向填写的服务商发送一句“请只回复 OK。”；该操作可能产生少量 API 费用，不会改变已保存配置。连接测试验证普通 Chat Completions 消息，不证明该服务商支持流式输出、图片或工具调用。
4. 打开启用开关并保存。**保存后必须在系统应用设置中强行停止 Aicy，再重新打开；本版配置在目标进程启动时读取，不热加载。** 关闭接管后同样要重启 Aicy。

本地保存前会检查 HTTPS 地址、模型非空和密钥换行。保存不进行网络请求；模型是否实际存在、额度和访问权限只能由服务商验证。错误信息仅显示固定描述或 HTTP 状态码，不显示服务商原始错误、密钥或消息正文。

## 私有存储

`ConfigStore` 使用模块私有 SharedPreferences。API Key 以 Android Keystore 中 AES-256/GCM 密钥加密，随机 IV 与密文保存于私有偏好，密钥不导出。备份和设备迁移规则均排除应用数据。解密失败时配置保持关闭，并提示重新输入密钥。

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

使用 JDK17、Gradle8.13、AGP8.13.2、官方 `platforms;android-37.0` 与 `build-tools;37.0.0`。`compileSdk 37` 必须同时设置 `compileSdkMinor 0`，以识别官方新平台包名。目标 SDK35，最低 Android8/API26。API102 使用官方 Maven Central AAR `io.github.libxposed:api:102.0.0` 作为 `compileOnly`，不把 API 实现打入 APK。

官方 API102 AAR 声明 `minCompileSdk=37`，因此没有沿用较低编译 SDK 或修改依赖元数据。AGP8.13.2 会提示其最高测试平台为36.1；该提示保留，AAR 元数据校验正常启用。

当前已在真实 SDK37.0 上通过完整调试/发布构建和 lint，包括 Manifest、XML、API102 AAR 元数据、Java 和 DEX 检查。签名及打包结果见根目录 [VALIDATION.md](../VALIDATION.md)。没有连接用户真机，因此不声称 Keystore、跨进程验签或 Hook 已完成设备验证。
