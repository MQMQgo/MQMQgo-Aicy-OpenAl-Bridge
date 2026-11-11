# Hook 映射与请求路径

以下名称为提供的 Aicy 13.1.1 APK 的真实 DEX 名称，已由 `tools/verify_target_apk.py` 核对，不能用于推断其他版本。

| 位置 | 作用 |
| --- | --- |
| `Application.attach(Context)` | API102 的 `onPackageReady` 后取得目标 Context，检查包名和版本，读取受限 Provider 的配置 |
| `com.flyme.agentos.llm.impl.routing.o.c(ModelFunction,String)` | 对实际 LLM 绑定子类 `routing.m` 返回自有 API 的 `W6.f` 快照，保持其他客户端种类和能力信息 |
| `C6.L` / `W6.u` | 构造用户模型 Profile 与配置有效性结果，不改变账号权益 |
| `com.flyme.agentos.llm.impl.openai.k` 构造器 | 替换 LLM 配置和独立 HTTP 客户端，关闭该 LLM 客户端的厂商网络遥测回调 |
| `openai.f` 的字段 `b` / `d` | 使用原生 holder 与原生引用计数，在首次请求前放入独立 HTTP 客户端；保留原生 Flow 和取消处理 |
| `openai.k.close()` | 补充释放模块拥有的 streaming holder 和对应连接，避免每次请求创建连接池 |
| `openai.n.a(Qg.c,Map)` | 保留 Bearer 和内容类型，省去厂商归因头 |
| `D9.c.Q(OutputStream,Y6.i,boolean)` | 原生完成消息、图片及工具序列化后，只保留标准 Chat Completions 顶层字段并覆盖模型；缓冲上限 32 MiB |
| `openai.l.toString()` / `C6.L.toString()` | 对本模块创建的配置返回脱敏文本 |

配置在 Aicy 进程启动时固定，修改后必须停止并重新启动 Aicy。关闭配置时不安装接管 Hook。

全部反射入口先解析，再启用修改。模型路径使用 API102 的 `ExceptionMode.PASSTHROUGH`，使接管错误进入原生错误处理，避免框架忽略 Hook 错误后恢复原请求。注册未完成时，已注册的适配 Hook 保持原行为。

协议归一化保留消息内部的 `tool_calls`、`tool_call_id`、工具结果以及多模态内容，不干预工具执行、权限、确认或校验。原生 `AgentLoop` 为 `com.flyme.agentos.agent.core.loop.O`；前置模型档位检查仅验证目录可用性，积分错误是云端模型请求后的处理路径，因此没有会员状态 Hook。

静态核对不能验证设备上的框架加载、ART 编译行为或实际网络服务，仍需真实手机运行验证。
