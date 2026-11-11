# 验证记录

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
