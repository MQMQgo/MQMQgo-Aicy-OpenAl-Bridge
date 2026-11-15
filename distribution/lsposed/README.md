# Aicy OpenAl Bridge

为魅族 **Aicy 助手 13.1.1（13001001）** 接入自有 OpenAI 兼容 Chat Completions API 的现代 LSPosed 模块，要求 **libxposed API102**，Android 8.0（API26）及以上。

> **本项目是 AI 辅助氛围编程（Vibe Coding）的产物。** 代码、界面、测试和文档主要由 AI 根据维护者需求辅助生成、修改，维护者负责使用反馈与发布决策。This project is a product of AI-assisted vibe coding.
>
> **当前为兼容修复候选。** 已通过本地构建、测试和签名检查，尚未完成真实 Flyme / API102 的完整运行验证；此前反馈的启动闪退是否恢复仍待真机确认。

## 功能

- 配置 HTTPS API 地址、API Key 和模型，支持从服务商 `/models` 获取、搜索和选择模型，也支持手填。
- 保留 Aicy 本机的对话、流式响应和 Agent 工具循环；工具调用和视觉任务需要所选模型具备相应能力。
- Miuix 设置界面、紧凑玻璃悬浮底栏及独立日志页，支持深浅色模式。
- API Key 通过 Android Keystore 加密存储；最近 200 条日志仅包含请求概况，不记录密钥、对话或响应正文。

## 安装

1. 从 Releases 安装包名为 `io.github.mqmqgo.aicybridge` 的签名 APK。
2. 在支持现代 API102 的 LSPosed 或兼容框架中启用模块，作用域选择 `com.meizu.voiceassistant`。
3. 填写渠道、测试连接并保存启用。强行停止并重新打开 Aicy；首次启用按框架提示重启。

源码仓库以前发布的 `io.github.aicybridge` 是另一安装身份，**配置和日志不互通，需要在新包中重新填写渠道**。旧包数据不会删除；只启用一个版本，避免重复 Hook。此后相同包名、相同签名的更新可以覆盖安装。

连接测试成功不等于工具调用或视觉能力可用；日志中的 HTTP 成功也不代表整个 Agent 任务完成。当前仅适配 Aicy 13.1.1，其他版本的 Hook 不会安装。修改 API 配置后需要重新启动 Aicy。

## 源码、验证与反馈

- [源码及完整文档](https://github.com/MQMQgo/MQMQgo-Aicy-OpenAl-Bridge)
- [验证记录](https://github.com/MQMQgo/MQMQgo-Aicy-OpenAl-Bridge/blob/main/VALIDATION.md)
- [问题反馈](https://github.com/MQMQgo/MQMQgo-Aicy-OpenAl-Bridge/issues)

反馈闪退时请提供模块版本、框架版本、Aicy 版本和相关异常堆栈，先删除其中的密钥及对话等私人内容。模块设置界面使用 [Miuix](https://github.com/compose-miuix-ui/miuix)；相关第三方许可见源码仓库 `THIRD_PARTY_NOTICES.md`。
