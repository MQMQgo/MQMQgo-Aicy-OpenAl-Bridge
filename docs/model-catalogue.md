# 获取服务商模型

在添加或编辑渠道时，先填写 HTTPS API 地址及 Key，再点击模型 ID 下的“获取模型”。无需先填写模型 ID 或保存草稿。模块读取标准 OpenAI 模型列表，展示搜索和选择对话框；选择后更新模型 ID，点击保存才改变已保存渠道。

| 输入地址 | 请求地址 |
| --- | --- |
| `https://example.test/v1` | `https://example.test/v1/models` |
| `https://example.test/v1/chat/completions` | `https://example.test/v1/models` |
| `https://example.test/api/tenant` | `https://example.test/api/tenant/models` |

请求使用 GET 和可选的 Bearer Key，不发送对话。禁止 HTTP、地址内凭据、查询参数和片段，且不跟随重定向。读取标准 `data[].id`，过滤空值、重复和控制字符，再按模型 ID 排序；响应上限为 4MiB，列表上限为 5000 条。

## 编辑与取消

已保存 Key 留空时，仅在目标域名和 HTTPS 端口未改变时沿用原密钥；无法解密时提示重试，更换服务商时要求明确填写 Key。获取过程不会覆盖原配置。

获取中可取消。更改地址、Key 或退出编辑会取消原请求并失效旧列表，避免旧服务商结果覆盖当前选择。列表仅保存在 ViewModel 内存中，不写入偏好或 Activity Bundle。接口未提供列表或当前 Key 无读取权限时，可继续手填模型。

模型列表只表示服务商返回的 ID，不保证模型支持图片、流式输出或工具调用。连接测试和服务商说明仍用于确认这些能力。

日志中只增加“模块 · 模型列表”的时间、服务商域名、HTTP 状态与耗时，不保存返回列表、请求头或 Key。
