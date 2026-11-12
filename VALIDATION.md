# v0.3.0 验证记录

日期：2026-11-12。新增本机调用日志，保留 v0.2.1 的启动资料隔离与现有渠道存储。

## 测试与界面

50 项 Gradle 本地测试通过，无失败、错误或跳过：协议 16 项、配置存储 8 项、原设置页 3 项、delegate 范围隔离 5 项；新增日志隐私 4 项、Provider 调用边界 2 项、持久化与保留上限 6 项、网络请求透明性 4 项、日志界面 2 项。

日志测试覆盖新实例恢复、200 条上限、停用后保留历史、在途请求结束、乱序响应、重定向失败、重复结束、清空后忽略迟到记录、拒绝原始错误及额外敏感字段。界面测试验证复制、失败筛选、确认清空、深色显示与开关重开保持。截图位于 [调用日志浅色](docs/screenshots/call-logs-light.png)、[深色](docs/screenshots/call-logs-dark.png)，使用 Robolectric 的示例数据。

另运行 1 项生成桥接类的 JVM 测试，实际执行生成字节码，验证响应对象不变、检查异常 IOException 保留同一实例。D8 再生成的桥接包装代码与提交文件逐字节一致，工作流通过 actionlint 检查。

## 打包与接口

- 调试及发布构建、lint 通过：0 Error/Fatal，6 Warning。依赖和工具链与上一版本相同，构建使用离线缓存。
- 官方原 APK SHA-256 及 25 个类的反射 ABI 核对通过，包含新增 OkHttp 拦截器、调用链、取消状态和响应码字段。
- API102 入口、作用域、元数据及 compileOnly 打包检查通过。主 DEX 共 14,686 个应用及依赖类；嵌入的 1008 字节 DEX 仅含 1 个模块自有日志桥接类。
- 嵌入桥接的校验和、接口、方法描述符和无异常包装转发结构均通过核对；未打包厂商实现或接口 Stub。
- 官方 apksigner 的 v2/v3 签名及 zipalign `-c -P 16 4` 检查通过。

APK：`aicy-openai-bridge-v0.3.0-api102.apk`，7,485,175 字节；SHA-256：`19bbbb69dbfb8ddceb8a8caac89ff00ce045a9a34f768a0ab61d9a2e51959238`。

包名 io.github.aicybridge，版本 0.3.0（4），minSdk 26 / targetSdk 35。发布证书 SHA-256 仍为 `85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf`，可保留数据覆盖安装旧发布包。

## 验证范围

HTTP 日志只统计至响应头到达，不验证流式生成或 Agent 任务完成；连接测试记录完整请求。未发送真实模型请求、使用用户 API Key 或上传用户日志。存储与界面使用 Robolectric，生成转发类使用 JVM 测试；尚无本版本在真实 Flyme、API102、ART 类加载及第三方服务上的运行结果。记录和复制范围见 [调用日志](docs/call-logs.md)。

---

# v0.2.1 验证记录（历史）

日期：2026-11-12。此次修复针对用户日志中 Aicy 13.1.1 启用第三方模型后的启动闪退。只定向读取了压缩包目录和目标进程的崩溃段；原始日志、设备属性、其他进程记录均未加入仓库。

## 原因与修改

同日的目标进程崩溃记录重复出现：`IllegalStateException: 模型无对应的媒体分析限额配置`，根调用在 `com.flyme.agentos.app.phone.toolprovider.Z` 构造器。原 APK 的 DEX 显示，该构造器通过 LLM 绑定的快照读取模型名，再查找内置 MediaAnalyseLimits。v0.2.0 全局替换该快照，使启动工具读到了自定义模型名。

v0.2.1 对原生 LLM delegate 工厂的两个 Kotlin invoke 入口建立线程内范围，仅在同步创建实际请求客户端时替换 Profile 与 HTTP 配置。启动、媒体工具和范围外读取返回原快照。范围使用 finally 恢复，支持嵌套与异常退出，与其他线程隔离。没有添加模型限额假数据，也未修改或吞掉媒体工具的异常。

## 验证结果

- 32 项本地测试通过：原有协议 16 项、配置存储 8 项、Compose 界面 3 项，以及新增 DelegateScope 5 项。
- 新测试覆盖未知自定义模型时启动仍能查询原媒体限额、请求创建时使用自定义模型、退出及失败后恢复原资料、嵌套和并发隔离。使用独立测试数据，未打包厂商实现或用户日志。
- 调试/发布构建与 lint 通过：0 Error/Fatal，6 Warning。测试、构建使用与 v0.2.0 相同的官方依赖和工具链。
- 原始 APK SHA-256 及 21 个类的反射 ABI 核对通过，包含工厂的 typed 和 Object bridge 两个 invoke 描述符。
- API102 元数据、入口、作用域、compileOnly 打包检查通过；APK 共 14,624 个应用及依赖类。
- 官方 apksigner 验证 v2/v3 签名通过，zipalign `-c -P 16 4` 通过。包名为 io.github.aicybridge，版本 0.2.1（3），minSdk 26 / targetSdk 35。

APK 为 `aicy-openai-bridge-v0.2.1-api102.apk`，7,452,407 字节，SHA-256：`4ec8a14c8ff1d69f6a606ca64c2a5e784dd763da9ace777ea6ea48a5c2b19add`。

发布证书 SHA-256 仍为 `85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf`；可覆盖安装旧发布包，原配置存储和密钥别名均保留。

当前 adb 设备列表为空。用户日志能够确认旧版故障路径；新增测试和静态核对不能替代修复后在用户 Flyme、LSPosed 与第三方 API 上的复测，尚无修复后的真机结果。

---

# v0.2.0 验证记录（历史）

日期：2026-11-12。当前发布版本为 v0.2.0，适配 Aicy 13.1.1（13001001）。原始 APK 与目标签名 SHA-256 见 README。

## 本地回归测试

Gradle 9.7.1；构建使用 JDK 17.0.21，Android 单元测试由 Java 21 启动。`core:test` 和 `app:testDebugUnitTest` 均成功。

| 测试类 | 运行 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| OpenAiClientTest | 6 | 0 | 0 | 0 |
| ProtocolTest | 8 | 0 | 0 | 0 |
| ChatRequestSanitizerTest | 2 | 0 | 0 | 0 |
| ConfigStoreTest | 8 | 0 | 0 | 0 |
| SettingsScreenTest | 3 | 0 | 0 | 0 |

共 27 项。协议测试覆盖多模态和工具字段保留、SSE 边界及分片、提供商错误、断流、取消与魅族参数清理。HTTP 使用测试替身，未访问真实模型或使用用户 API Key。

配置存储测试使用 Robolectric SDK29 与可控的 SecretCodec 替身，验证旧版无标记记录恢复、新实例读取、停用后保留渠道、空编辑密钥保留密文、加密失败不覆盖记录、临时解密或 Provider 异常后恢复，以及更换主机时不默用原密钥。它们没有验证硬件 Keystore。

界面测试使用 Robolectric SDK35 原生图形渲染与实际 SettingsActivity，验证首次进入空配置页面、深色编辑页面，以及通过界面保存、停用、关闭 Activity 后新建 Activity 时地址、模型和开关保持。界面测试使用无密钥的测试渠道；关闭 Activity 不等同于杀死 Android 进程。浅色、深色和已保存渠道的渲染截图位于 [docs/screenshots](docs/screenshots)。

DevicePersistenceTest 的真实 AndroidKeyStore 测试源码与测试 APK 已构建，使用独立测试偏好和别名。没有连接可运行的 Android 设备，因此未执行此组设备测试，也不计入上述 27 项。

## Android 构建与检查

使用官方 libxposed 102.0.0 AAR、Android SDK 37.0、build-tools 37.0.0、AGP 9.5.1、Kotlin/Compose 编译插件 2.4.21 和 Miuix 0.9.4：

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug
```

完整任务成功。没有修改官方 AAR、关闭依赖元数据检查或使用框架/厂商实现 Stub。Miuix 的完整 Apache 2.0 许可证已核对并随 APK 打包。

lint：0 个 Error/Fatal，6 个 Warning，涉及非公开 Application.attach、targetSdk、测试依赖版本、空 super 调用，以及运行时鉴权的导出 Provider。Android 8.0 主题中不支持的导航栏属性已移除，导航栏由 AndroidX enableEdgeToEdge 按系统版本处理。AGP 的旧 DSL/Kotlin 插件兼容选项会输出后续迁移提醒，未关闭相关检查。

本次没有修改原生 Hook、工具发现或协议核心。v0.1.0 已完成原 APK 的静态 ABI 核对：原文件 SHA-256 相符，20 个类中用到的方法和字段描述符全部匹配；此记录不代表真机运行验证。

## 发布 APK

| 项目 | 结果 |
| --- | --- |
| 文件 | `aicy-openai-bridge-v0.2.0-api102.apk` |
| 字节数 | 7,452,407 |
| SHA-256 | `7f5e8cbaacf9101664dcaf9aab2a350f01d86fd40e485a4cfe3756f7f53f0c71` |
| 发布证书 SHA-256 | `85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf` |
| 包名 / 版本 | `io.github.aicybridge` / `0.2.0`（2） |
| minSdk / targetSdk | 26 / 35 |
| 签名 | 官方 apksigner 验证 v2、v3 通过 |
| 对齐 | 官方 zipalign `-c -P 16 4` 检查通过 |
| API102 元数据 | 入口、作用域、min/target API 均通过打包检查 |
| DEX | 共 14,619 个应用及依赖类；没有打包 libxposed API 或厂商实现 |

发布证书与本仓库 v0.1.0 相同；保留原偏好文件、Keystore 别名和密文格式，可覆盖安装保留应用数据。发布密钥位于源码仓库之外；CI 调试包使用不同签名。

## 设备验证的范围

尚未在用户手机上复现旧版配置清空的确切触发条件。本次修正保存确认、渠道存在状态、编辑密钥保留和异常恢复，并通过上述回归测试。已经被卸载或系统清除的应用数据无法凭新版本恢复。

没有真实 Flyme 手机、API102 框架或用户第三方模型的端到端运行结果。因此不能把编译、签名、Robolectric 页面重开或静态 ABI 检查等同于硬件 Keystore、进程重启、框架加载、聊天、读屏或工具执行已在真机通过。

## GitHub 与远端 CI

发布目标为用户指定的私有仓库 MQMQgo/MQMQgo-Aicy-OpenAl-Bridge。Release 附件上传接口在首次发布时返回 HTTP 401，因此签名 APK 保存在仓库 dist/，Release 页面提供该文件入口。未上传签名私钥。

首次发布的 GitHub Actions 两次 push 运行在任务启动前返回 startup_failure，任务列表为空；读取 Actions 管理设置返回 403，尚未确定 GitHub 端原因。本次工作流已增加 Java21 和 Android 单元测试，并通过官方 actionlint 1.7.7 语法检查。以上构建、测试与 lint 通过记录均来自本地环境，不代表远端 Actions 已通过。

---

# v0.1.0 验证记录（历史）

日期：2026-11-11。适配对象为用户提供的 Aicy 13.1.1（13001001），原始 APK SHA-256 见 README。

## 协议单元测试

JDK 17.0.21，Gradle 8.13，任务 `:core:test` 执行成功。

| 测试类 | 运行 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| OpenAiClientTest | 6 | 0 | 0 | 0 |
| ProtocolTest | 8 | 0 | 0 | 0 |
| ChatRequestSanitizerTest | 2 | 0 | 0 | 0 |

共 16 项。覆盖请求深拷贝及多模态/工具字段保留、CRLF/BOM/多行 SSE、事件大小限制、交错工具分片、提供商错误、缺少结束标记、调用取消、成功完成，以及魅族专用参数清理和工具结果回合保留。

HTTP 连接使用测试替身；没有发送真实 API 请求或使用用户 API Key。

## 原 APK 静态核对

`python3 tools/verify_target_apk.py 原APK` 成功：原文件 SHA-256 相符，20 个类中用到的方法与字段描述符全部匹配。APK Manifest 和签名也已通过官方 Android build-tools 读取。Manifest 显示 Agent 位于主进程；显式的其他进程属于旧语音唤醒、识别和 TTS。

本地工具元数据排序曾通过 5 个独立样例检查，涉及中文、英文、全角字符和原生 examples。工具执行和授权本身未替换。

## 完整 Android 构建

使用官方 libxposed `102.0.0` AAR、Android SDK 37.0、build-tools 37.0.0、AGP 8.13.2 和 Gradle 8.13：

```sh
./gradlew :core:test :app:assembleDebug :app:assembleRelease :app:lintDebug
```

完整任务成功。最后修改 Hook 异常模式与图标后，重新执行调试/发布构建和 lint 也成功。没有修改官方 AAR，没有关闭依赖元数据检查，没有使用厂商类或框架 API 的自制 Stub。

lint：0 个 Error/Fatal，11 个 Warning。警告包括非公开 `Application.attach`、targetSdk 和 AGP 版本提示、运行时鉴权的导出 Provider，以及中文界面的翻译提示。AGP 还提示其最高测试 SDK 是 36.1；实际 SDK 37.0 的编译、资源与 DEX 构建均通过。

## 发布 APK

| 项目 | 结果 |
| --- | --- |
| 文件 | `aicy-openai-bridge-v0.1.0-api102.apk` |
| 字节数 | 45,980 |
| SHA-256 | `8f4ae8fa5efca37d01323e588ef730ef49909c44cbad9615640c4edf856977fe` |
| 发布证书 SHA-256 | `85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf` |
| 包名 / 版本 | `io.github.aicybridge` / `0.1.0`（1） |
| minSdk / targetSdk | 26 / 35 |
| 签名 | 官方 apksigner 验证 v2、v3 通过 |
| 对齐 | 官方 zipalign 检查通过 |
| API102 元数据 | 入口、作用域、min/target API 均通过打包检查 |
| DEX | 61 个模块类；没有打包 libxposed API 实现或厂商实现 |

发布密钥使用项目独立的 RSA 3072 密钥，存放在源码仓库之外。发布包与 CI 调试包签名不同。

## APK 分析包脚本

使用人工构造的 APK 验证多 DEX、Manifest、资源表、小型 assets 文件均原样保留；大模型资源与原生库内容排除，并保留完整原始文件目录。相同输入输出路径会被拒绝。未安装或修改任何真实 APK。

## 尚未验证

执行 `adb devices -l` 时没有连接设备；没有真实 Flyme 手机、API102 框架或用户第三方模型的端到端运行结果。因此不能把编译、签名和静态 ABI 检查等同于聊天、读屏、工具执行或框架加载已在真机通过。安装后的设备验证仍需进行。

## GitHub 发布（2026-11-12）

源码和 `v0.1.0` 标签已上传到用户指定的私有仓库 `MQMQgo/MQMQgo-Aicy-OpenAl-Bridge`。Release 附件上传接口返回 HTTP 401，因此签名 APK 发布在仓库 `dist/` 中，Release 页面提供该文件的下载入口。没有上传签名私钥。

通过 GitHub Contents API 读回远端 APK，确认 45,980 字节、SHA-256 与上述本地签名包一致。

GitHub Actions 的两次 push 运行在任务启动前返回 `startup_failure`，任务列表为空；没有执行远端构建。工作流文件通过官方 actionlint 1.7.7 的语法检查，当前连接读取 Actions 管理设置返回 403，尚未确定 GitHub 端启动失败的原因。上述构建与测试通过记录均来自本地执行环境，不代表 GitHub Actions 运行通过。
