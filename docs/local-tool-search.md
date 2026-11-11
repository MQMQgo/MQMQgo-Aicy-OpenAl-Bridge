# Aicy 13.1.1 本地工具发现适配

本适配仅在启动时启用用户自己的模型 API 后安装；更改配置需重启 Aicy。它关闭工具发现的云端向量、Rerank 和模型二次 query 改写，并在原有规则匹配结果上补充本地工具元数据召回。不改变会员状态，不执行工具，不修改工具授权、动作确认或 verifier。

## APK 中核实的接口

以下名称均来自用户提供的 `com.meizu.voiceassistant` 13.1.1 / versionCode `13001001`；混淆名称仅适用于这个版本。

| 实际类 / 成员 | 证据与用途 |
| --- | --- |
| `agent.domain.tool.recall.y` 构造器，27 参数 | `ToolRecallRuntimeConfig`；参数 1 是 ruleEnabled、3 是 vectorEnabled、4 是 rerankEnabled、6 是 queryRewriteEnabled，以上从 0 开始计数。最后参数 26 是默认参数掩码；bit 2/8/16/64 会覆盖相应实参，Hook 同时清除这些 bit。 |
| `agent.domain.tool.recall.p.i(core.tool.J, ArrayList)` → `Set<S>` | 原有关键词与正则规则匹配。输入是 selector 已筛选的候选池。保留原结果，再按名称、描述、`S.u()` 关键词、`S.o()` 示例匹配中文双字片段和英文单词。 |
| `agent.core.tool.J.b()` / 字段 `j` | 当前 query / 原生最大工具数量。额外召回只填充 `max(0, j - 原规则命中数)` 个位置；不删除原规则结果。 |
| `agent.domain.tool.recall.A.c(S)` | 原生索引文本以 `getDescription()` 建 description 文档，并以 `S.o()` 建 examples 文档，证明 examples 的真实来源。 |
| `agent.domain.tool.u.l(J, ContinuationImpl)` | `ToolSubsetSelector.selectDetailed`：先检查 `S.x()` 前置依赖、registry 开关和 `CallableFrom.CLIENT_ONLY`，然后才调用规则匹配。保留原生互斥、历史继承、分组与预算处理。 |

完整包名前缀为 `com.flyme.agentos.`。源码分析位置：

- `analysis/selector-fallback/sources/com/flyme/agentos/agent/domain/tool/u.java:2354` 起：依赖、开关和调用来源过滤。
- 同文件 `:2652` 起：ruleEnabled 时调用 `p.i`；`:2733` 起：vectorEnabled 控制向量阶段。
- `analysis/aicy-focused/sources/com/flyme/agentos/agent/domain/tool/u.java:940` 起：Rerank 受 config.rerankEnabled 控制；`:854` 起：默认 STRICT 策略在语义召回错误时抛异常，不能假定会自动回退。
- `analysis/aicy-focused/sources/com/flyme/agentos/agent/domain/tool/recall/y.java:91` 起：真实构造参数、默认掩码及赋值。
- `analysis/aicy-focused/sources/com/flyme/agentos/agent/domain/tool/recall/A.java:78` 起：原生 description/examples 索引文本。

## 边界

关闭 vector 后，原生 selector 还会将其他可用候选以低分加入选择报告，再由现有 Agent 上下文预算处理（`selector-fallback/.../u.java:4268` 起）。本适配保留该行为，因此“本地额外命中上限”不代表最终整个 tools 数组只包含这些命中。这样可维持原生候选与安全处理；元数据召回的相关性也不等同于语义向量搜索。

原规则匹配只在 selector 规定的轮次运行，插件保留这一条件。用户输入和工具参数均不写日志。本地打分是同步、有限长字符串操作，不请求 Embedding 或 Rerank 服务。联网搜索、图片生成、远程 MCP、技能自身的服务仍各自需要可用授权。

本文件已以 Java 8、真实 libxposed API 102 与 Android 37 API classpath 编译，并通过 5 项本地元数据排序样例检查（中文、英文、全角英文与 examples）；协议核心另有 JVM 测试。本 Hook 仍需要在已安装该版本的真实手机上验证，不能以静态分析代替设备测试。
