# ADR-003：使用自定义 Port 隔离 Spring AI 与模型 Provider

## 状态

Accepted

## 日期

2026-09-02

## 背景与问题

记忆提取、回答生成、Embedding 和评测都需要模型能力。直接在领域或用例代码中引用 Spring AI 的 `ChatModel`、`EmbeddingModel`、`Document` 或 Provider DTO，会使核心逻辑难以脱离框架测试，也会把不同模型的行为差异带入实验。

Spring AI 的 Chat Memory 主要管理会话上下文窗口和消息存储；RecallTree 研究的是跨会话、可版本化、可冲突消解的长期语义记忆，两者不能混为同一边界。

## 决策

1. Application 模块定义框架无关的 `ChatModelPort`、`EmbeddingModelPort`、请求、响应、流式事件和调用元数据。
2. Infrastructure 模块使用 Spring AI 实现 OpenAI-compatible 与 Ollama Adapter。
3. Spring AI Chat Memory、Advisor Memory 或 VectorStore 不得作为 RecallTree 长期记忆核心；它们只能作为基线或外围适配，且必须显式标注。
4. 必须提供确定性的 Fake Chat/Embedding Adapter，使默认单元与端到端测试不访问网络、不消耗付费 Token。
5. 结构化输出是“不可信输入”：转换为 Java 类型后必须执行 Schema/Bean Validation；解析失败、缺字段和越界值不得静默写入记忆。
6. 每次调用记录 Provider、模型、模型修订、Prompt 版本、温度、Token 使用、延迟、重试和 trace id。日志不得记录凭据；敏感 Prompt/响应默认不进入普通日志。
7. 超时、重试、限流和取消必须有明确上限。数据库事务不得跨越外部模型调用。
8. 流式模型输出由 Adapter 转成项目定义的事件；SSE 是 API 层传输细节，不进入 Domain。
9. 评测必须固定模型配置和 Prompt 版本，任何回退或重试切换 Provider 都要写入原始结果。

## 备选方案

- 业务代码直接使用 Spring AI：代码更少，但核心测试和实验被框架类型绑定。
- 直接使用厂商 SDK：能访问专有能力，但多 Provider 切换、Ollama 和 Fake 测试需要重复适配。
- 完全自研 HTTP 客户端：控制最强，但会重复实现鉴权、流式协议、模型选项和响应解析。
- 使用 Spring AI Chat Memory：适合滑动窗口基线，不满足长期版本、冲突、时效、来源和删除要求。

## 后果

- 正面影响：核心方法可独立验证；模型可替换；离线 CI 稳定；实验配置可追溯。
- 负面影响 / 风险：需要维护项目 DTO 与 Spring AI DTO 的转换；部分 Provider 专有能力需要显式扩展。
- 迁移与恢复：新增 Provider 只添加 Adapter 与配置；Spring AI 升级限制在 Infrastructure 模块，并通过契约测试验证行为。

## 参考资料

- [Spring AI Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
- [Spring AI Embedding Model API](https://docs.spring.io/spring-ai/reference/api/embeddings.html)
- [Spring AI Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html)
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
