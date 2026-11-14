# Hook 映射与请求路径

以下名称为提供的 Aicy 13.1.1 APK 的真实 DEX 名称，已由 `tools/verify_target_apk.py` 核对，不能用于推断其他版本。

| 位置 | 作用 |
| --- | --- |
| `Application.attach(Context)` | API102 的 `onPackageReady` 后取得目标 Context，检查包名和版本，读取受限 Provider 的配置 |
| `routing.LlmBindings$LlmClientBinding$delegateSlot$2.invoke()` | 覆盖 Kotlin 返回原生客户端和返回 Object 的两个入口，在同步创建 LLM 客户端期间建立线程内替换范围，finally 恢复原状态 |
| `com.flyme.agentos.llm.impl.routing.o.c(ModelFunction,String)` | 仅在上述创建范围内，对实际 LLM 绑定子类 `routing.m` 返回自有 API 的 `W6.f` 快照；其他读取返回原对象 |
| `C6.L` / `W6.u` | 在创建范围内替换路由凭据与配置有效性；内部模型标识、模态、限额始终保留原值，不改变账号权益 |
| `com.flyme.agentos.llm.impl.openai.k` 构造器 | 仅在上述创建范围内替换 LLM 配置和独立 HTTP 客户端，关闭该 LLM 客户端的厂商网络遥测回调 |
| `openai.f` 的字段 `b` / `d` | 使用原生 holder 与原生引用计数，在首次请求前放入独立 HTTP 客户端；保留原生 Flow 和取消处理 |
| `openai.k.close()` | 补充释放模块拥有的 streaming holder 和对应连接，避免每次请求创建连接池 |
| `openai.n.a(Qg.c,Map)` | 保留 Bearer 和内容类型，省去厂商归因头 |
| `D9.c.Q(OutputStream,Y6.i,boolean)` | 原生完成消息、图片及工具序列化后，只保留标准 Chat Completions 顶层字段并覆盖模型；缓冲上限 32 MiB |
| `openai.l.toString()` / `C6.L.toString()` | 对本模块创建的配置返回脱敏文本 |

配置在 Aicy 进程启动时固定，修改后必须停止并重新启动 Aicy。关闭配置时不安装接管 Hook。

全部反射入口先解析，再启用修改。模型路径使用 API102 的 `ExceptionMode.PASSTHROUGH`，使接管错误进入原生错误处理，避免框架忽略 Hook 错误后恢复原请求。注册未完成时，已注册的适配 Hook 保持原行为。

v0.2.1 修正启动时的模型资料污染：原生媒体工具 `com.flyme.agentos.app.phone.toolprovider.Z` 构造时通过绑定快照取得模型名，再查找内置 `MediaAnalyseLimits`；将未知自定义模型名用于这个读取会抛出 IllegalStateException，导致工具与页面的依赖注入失败。现在该读取收到原生快照，自定义快照只供同步创建请求客户端使用。嵌套调用、异常退出和并发线程均通过 DelegateScope 回归测试；没有增加自定义限额、模型别名或吞掉该构造器异常的 Hook。

v0.4.1 进一步收紧这个边界：创建范围内的 `C6.L.e` 也保留原生模型名，第三方模型仅写入真正使用的 `openai.l` 配置与请求正文。新增回归覆盖客户端创建中途的媒体限额读取，保留原 token 预算、输入模态和限额对象。`provider` 继续使用 `openai-compatible`，让原生出站策略维持凭据脱敏。

协议归一化保留消息内部的 `tool_calls`、`tool_call_id`、工具结果以及多模态内容，不干预工具执行、权限、确认或校验。原生 `AgentLoop` 为 `com.flyme.agentos.agent.core.loop.O`；前置模型档位检查仅验证目录可用性，积分错误是云端模型请求后的处理路径，因此没有会员状态 Hook。

静态核对不能验证设备上的框架加载、ART 编译行为或实际网络服务，仍需真实手机运行验证。
