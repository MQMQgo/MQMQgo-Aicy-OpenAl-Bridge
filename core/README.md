# OpenAI 协议核心

纯 Java 8 的 Chat Completions 实现，不包含 Aicy Hook、订阅状态修改或设备工具执行器。Android 使用系统 `org.json`；桌面程序运行时需自行提供 `org.json:json:20240303`。

模块设置页用此客户端执行连接测试；实际 Aicy 对话复用目标应用原生的流式处理。`ChatRequestSanitizer` 则用于真实 Hook 的请求归一化，保留消息/工具内部结构，清除厂商专用顶层参数。

## 接入

```java
OpenAiClient client = new OpenAiClient(new OpenAiClient.Config(
        "https://your-provider.example/v1", apiKey, "your-model"));
JSONObject request = new JSONObject();
request.put("messages", messages);
request.put("tools", tools); // 可选，使用服务商支持的 OpenAI 工具定义

OpenAiClient.Call call = client.newCall(request);
// 在工作线程执行。chunk 保留本次增量，snapshot 为累计的不可变快照。
ChatCompletionAccumulator.Snapshot result = call.stream((chunk, snapshot) -> {
    // 将 chunk 中的 delta 转成已验证的 Aicy 回调协议。
});
// 其他线程可调用 call.cancel()。
```

- `Call.execute()` 返回非流式原始 `JSONObject`；`Call.stream(listener)` 返回完成后的累计快照。每个 Call 只能执行一次。
- Base URL 是服务商的 API 前缀，例如 `/v1`；也接受完整 `/chat/completions`。根 URL 追加 `/chat/completions`，不会擅自插入 `/v1`。
- `OpenAiRequest.copyForChat()` 深拷贝输入，只覆盖 `model` 和 `stream`，保留 `tools`、`tool_choice`、多模态消息及其他字段。
- `ChatCompletionAccumulator` 根据 choice index 和 tool index 拼接 `content`、`id`、函数名及参数。工具参数在增量阶段保持字符串，调用方应在完成后校验 JSON 和工具 schema，并判断 `finishReason`。
- `SseReader` 支持 BOM、LF/CRLF/CR、多行 `data`、注释和事件 ID。依据 EventSource 规则，不完整的 EOF 事件会被丢弃；客户端进一步要求 `[DONE]` 和各 choice 的 `finish_reason`。
- 不支持旧式 `function_call`、OpenAI Responses API 或厂商私有 SSE 事件格式，会显式报错。

## 资源与错误处理

仅接受 HTTPS，使用平台证书校验，禁止自动重定向，防止将 bearer key 发送到跳转地址。默认连接超时 20 秒、读取超时 120 秒，可通过 Config 调整。调用取消会中断连接；实际网络栈解除阻塞的速度依平台实现而定。

请求和普通响应上限各 16 MiB，错误响应上限 64 KiB。SSE 默认每行上限 1 Mi 字符、每事件 2 Mi 字符、全流 32 Mi 字符，累计内容与工具字段上限 8 Mi 字符，每个响应最多 64 个 choice、每个 choice 最多 64 个工具调用。

调用方需处理 `OpenAiException`（含 HTTP 状态、服务商错误类型/代码）、`ProtocolException`（格式、断流或资源限制）与其他 `IOException`。取消抛出 `InterruptedIOException`。服务商错误消息最多保留 512 字符；本库没有日志输出。上层也不应记录请求内容、图片或密钥。

## 验证

在项目根目录执行 `./gradlew :core:test`。16 项测试使用脱敏的固定协议样本和模拟 HTTP 连接，不访问真实模型、不使用真实 Key。覆盖多模态请求、SSE 边界、工具参数分片、错误、断流、取消及厂商参数清理。真实设备行为仍需另行验证。
