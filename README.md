# Aicy OpenAI Bridge

为 **Aicy 助手 13.1.1** 接入自己的 OpenAI 兼容 Chat Completions API。使用现代 **libxposed API 102**，包含设置界面和真实 Hook，支持构建为可直接安装的 APK。

## 安装与配置

1. 下载并安装 [dist 目录中的签名 APK](dist/README.md)，版本说明同时发布在 Releases。
2. 在支持 **现代 API 102** 的 LSPosed 或兼容框架中启用模块，作用域选择 **Aicy 助手**（`com.meizu.voiceassistant`）。旧版 API 82 框架不能加载本模块。
3. 打开桌面的 **Aicy OpenAI Bridge**，填写 HTTPS API 前缀、API Key 和模型 ID。例如 `https://api.openai.com/v1`；也可填写完整的 `/chat/completions` 地址。
4. 可先点“测试连接”检查地址和密钥，然后点击“保存并启用”。已保存的渠道显示在“我的渠道”卡片中；修改时点击“编辑渠道”，密钥留空会保留原密钥。
5. **强行停止并重新打开 Aicy**，让进程加载新配置；框架首次启用模块时按管理器提示重启。修改或关闭配置后同样需要重启 Aicy。

在模块中刷新 Hook 状态可查看是否成功接管。连接测试只验证一次文字请求，不能据此判断模型是否支持工具调用或图片。Agent 需要模型支持 `tools` / `tool_calls`；读屏等图片任务还需要视觉能力。

## 接入范围

- 创建实际 LLM 请求客户端时替换模型、API Key 和接口地址，继续使用 Aicy 的消息、图片与工具序列化及流式响应处理。启动和工具元数据查询保留原生模型资料。
- 清除魅族网关专用的档位、会话、目录和计费参数；保留标准消息、工具调用与采样参数。
- 给模型请求创建独立的原生 Ktor/OkHttp 连接，不继承魅族账号鉴权、共享 Cookie 或请求插件；两层均关闭自动重定向。
- 保留手机端 Agent 循环、工具候选过滤、动作确认和验证链。工具发现改为原有规则加本地名称、描述、关键词和示例匹配，关闭云端 Embedding、Rerank 和二次查询改写。
- 不修改官方账号会员或积分状态。联网搜索、图片生成、远程 MCP、跨设备服务等工具仍取决于各自服务和授权。

API Key 使用模块 UID 下的 Android Keystore 加密存储，禁止备份。配置 Provider 只允许模块自身及包名、UID、签名均匹配的 Aicy 调用。启用后，Aicy 进程会接触 API Key，并把对话及任务所需内容发给你指定的服务商。

## v0.3.0 调用日志

在设置中点击“查看调用日志”，可查看 Aicy 模型请求和连接测试的时间、模型、域名、HTTP 状态与耗时，支持失败筛选、复制、清空和记录开关。默认只保留最近 200 条，不记录密钥、完整 URL、对话、工具参数或响应正文。界面沿用 Miuix，支持浅色与深色。

Aicy 流式请求记录到响应头到达；后续输出和 Agent 任务可能继续运行。日志写入为异步操作，失败不会改变原请求结果。详细范围与隐私说明见 [调用日志](docs/call-logs.md)，界面示例见 [浅色](docs/screenshots/call-logs-light.png)、[深色](docs/screenshots/call-logs-dark.png)。

## v0.2.1 启动闪退修复

旧版全局替换模型资料后，媒体分析工具会用自定义模型名查询 Aicy 内置限额表，遇到未知模型便在启动时抛出异常。v0.2.1 将替换限制在原生 LLM 客户端创建过程中；启动、媒体限额、工具初始化和其他线程的资料查询均继续使用原生记录。请求发送至服务商时仍使用模块保存的地址、密钥和模型 ID。

已经遇到启用后闪退的设备，可以先关闭模块或第三方模型开关，强行停止 Aicy；覆盖安装 v0.2.1 后重新启用并重启 Aicy。模块数据和保存的渠道会保留。

## 设置界面与配置恢复

设置界面采用 [Miuix 0.9.4](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4)，支持系统浅色与深色模式。渠道、连接测试和运行状态分组展示。目前保存一个 API 渠道。

保存成功后才显示“已保存”；退出重开会重新读取本机记录。关闭接管只改变开关，保留地址、模型和加密密钥。旧版记录可直接读取，密钥暂时无法解密时也会保留渠道，并允许重试读取。编辑时密钥留空不会覆盖原密钥；更换 API 主机时需明确填写密钥。

从本仓库旧版发布包更新时，直接覆盖安装最新版，保留应用数据。不要先卸载模块或清除其数据。界面预览见 [浅色](docs/screenshots/settings-empty-light.png)、[深色](docs/screenshots/settings-empty-dark.png) 和 [已保存渠道](docs/screenshots/settings-saved-light.png)，截图由 Robolectric 渲染。

## 版本与验证

| 项目 | 已适配值 |
| --- | --- |
| Aicy 包名 | `com.meizu.voiceassistant` |
| 版本 | `13.1.1` / `13001001` |
| 原始 APK SHA-256 | `eea6d390e6b1cbc3fb151354d354fe14516d44c6edd06fef9ca7571bc89d69af` |
| 目标签名 SHA-256 | `94153299d67ab1b37a39ed5fe17e893df87084fb129077be51863bcbd9132336` |
| 框架 API | 最低 102，目标 102 |
| Android 最低版本 | API 26 |

入口检查目标版本，配置读取检查目标签名。尚未适配其他 Aicy 版本。已执行协议测试、原 APK 的反射成员核对、APK 构建、lint 和签名检查；**尚未在真实 Flyme 手机和 API102 框架上运行验证**。具体结果见 [VALIDATION.md](VALIDATION.md)。

## 构建

使用 JDK 21、Gradle Wrapper 9.7.1、AGP 9.5.1、Android SDK `platforms;android-37.0` 和 `build-tools;37.0.0`：

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0'
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

Windows 使用 `gradlew.bat`。官方 `io.github.libxposed:api:102.0.0` AAR 要求 compile SDK 37；项目原样使用该依赖，没有打包框架 API 或厂商实现。Kotlin 与 Compose 编译插件固定为 2.4.21；Android Compose UI 测试使用 Java 21，应用源码目标为 JVM 17。

`assembleDebug` 输出带调试签名的开发 APK；`assembleRelease` 输出未签名包，需使用自己的长期签名密钥签名后分发。GitHub Actions 配置为执行测试、调试构建和 lint 并保存构建产物；远端启动情况见验证记录。CI 调试签名不作为长期更新签名。发布签名密钥不存入源码仓库。

保留原 APK 的电脑可以额外运行静态 ABI 核对（Python 3.11+）：

```sh
python tools/verify_target_apk.py 'Aicy 助手_13.1.1.apk'
```

源码仓库不包含原始 Aicy APK、反编译实现、真实 API Key 或发布签名密钥。接口映射说明见 [原生连接隔离](docs/native-http.md)、[本地工具发现](docs/local-tool-search.md) 和 [Hook 映射](docs/hooks.md)。
