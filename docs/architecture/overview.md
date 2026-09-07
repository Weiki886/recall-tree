# RecallTree 架构总览

本文档固定 RecallTree 首版的模块职责、依赖方向、同步与异步边界，以及一条可串联的端到端主干。约束以 [ADR-001](../adr/0001-java-modular-monolith.md)、[ADR-002](../adr/0002-postgresql-pgvector.md)、[ADR-003](../adr/0003-model-provider-boundary.md) 为准，本文不重复其取舍论证。

## 1. 系统边界

RecallTree 是可替换模型之上的长期记忆层，不训练基础模型，不复刻通用 Agent 平台。系统对外暴露三类能力：

- 带长期记忆的对话（个人学习与毕业设计助手场景）；
- 记忆的浏览、审阅、修正、导出与删除；
- 可复现的离线评测与消融分析。

外部依赖只有两类：Chat/Embedding Provider（OpenAI-compatible 或 Ollama）与 PostgreSQL 17 + pgvector。

## 2. 模块与依赖方向

```
          web (Vue 3 + TS)
               |  HTTP/SSE
               v
  +----------------------+        +----------------------+
  |        api           |        |     evaluation       |
  | Spring Boot 组合根    |        |  实验组合根            |
  +----------+-----------+        +----------+-----------+
             |                               |
             |        (两者互不依赖)           |
             v                               v
  +---------------------------------------------------+
  |                   application                     |
  |  用例、事务意图、输入/输出 Port                       |
  +----------------------+----------------------------+
                         |
                         v
  +---------------------------------------------------+
  |                     domain                        |
  |  聚合、值对象、领域服务、不变量（无 Spring 依赖）        |
  +---------------------------------------------------+
                         ^
                         | 实现 Port
  +---------------------------------------------------+
  |                 infrastructure                    |
  |  PostgreSQL/pgvector、Spring AI Adapter、Clock、Id  |
  +---------------------------------------------------+
```

编译期依赖方向固定为 `api / evaluation / infrastructure -> application -> domain`。`domain` 不依赖任何框架模块，`infrastructure` 只实现 `application` 声明的 Port，`api` 与 `evaluation` 作为两个独立组合根装配实现。该规则在 Issue #3 由 ArchUnit 断言。

| 模块 | 职责 | 禁止事项 |
| --- | --- | --- |
| `domain` | 记忆聚合、版本链不变量、冲突判定、时效与置信度衰减规则、评分融合公式 | 引入 Spring、JDBC、Spring AI、HTTP 类型 |
| `application` | 写入/读取用例编排、事务意图、`ChatModelPort` 等 Port 定义 | 依赖具体 Adapter、拼接 SQL、处理 SSE |
| `infrastructure` | Repository、pgvector 检索、Provider Adapter、时钟与 ID 生成、指标 | 承载领域规则或用例决策 |
| `api` | REST/SSE 端点、认证上下文、DTO 映射、错误模型 | 直接访问数据库或模型 SDK |
| `evaluation` | 数据集加载、基线、指标、报告 | 依赖 `api`，或复用其 HTTP 层 |
| `web` | 对话界面、记忆中心、版本时间线、检索轨迹可视化 | 实现记忆业务规则 |

### Port 清单

全部 Port 由 `application` 模块声明、`infrastructure` 模块实现，分两类：

| Port | 类别 | 职责 | 约束来源 |
| --- | --- | --- | --- |
| `ChatModelPort` | 模型 | 对话生成与结构化输出，含流式 | [ADR-003](../adr/0003-model-provider-boundary.md) |
| `EmbeddingModelPort` | 模型 | 文本转向量 | [ADR-003](../adr/0003-model-provider-boundary.md) |
| `MemorySearchPort` | 存储 | 语义候选（pgvector）与关键词候选召回，返回未排序候选集 | [ADR-002](../adr/0002-postgresql-pgvector.md) |
| `MemoryRepositoryPort` | 存储 | 记忆、版本、来源、冲突、审阅记录的读写与事务化版本切换 | [ADR-002](../adr/0002-postgresql-pgvector.md) |
| `MemoryTaskPort` | 存储 | 异步写入任务的登记、领取、重试与状态更新 | [ADR-002](../adr/0002-postgresql-pgvector.md) |
| `ClockPort` / `IdGeneratorPort` | 基础设施 | 可控时间与 UUIDv7 生成，保证衰减与分页逻辑可测试 | 本文档 |

两类 Port 的关键区别：模型类 Port 的输出是**不可信输入**，必须校验后才可落库；存储类 Port 的输出可信，但必须以 `ownerId` 为必填查询条件。

`MemorySearchPort` 只负责召回候选，**不负责排序**。多信号加权融合由 `domain` 的 `HybridScorer` 完成，这样评分公式可以用纯单元测试验证，也便于消融实验替换权重而不碰 SQL。

## 3. 同步与异步边界

首版只有一个进程，但读写路径的时延要求不同，边界必须显式区分。

| 路径 | 模式 | 约束 |
| --- | --- | --- |
| 记忆读取（检索 + 注入） | 同步，位于回答生成关键路径 | 有超时上限；检索失败降级为无记忆回答，不阻断对话 |
| 回答生成 | 同步流式，SSE 传输 | 流式事件由 Adapter 转为项目事件，SSE 仅存在于 `api` |
| 记忆写入（提取、分类、校验、冲突消解） | 异步，回答返回后触发 | 通过 `memory_task` 表落库驱动，可重试、可观测、幂等 |
| 时效衰减与遗忘 | 定时任务 | 只改状态与置信度，不物理删除 |
| 硬删除 | 同步用例 + 校验 | 事务内删除结构与向量，并留下不含内容的审计记录 |

外部模型调用一律不得包含在数据库事务内。写入流水线的顺序是：先调用模型得到候选结果，再在一个短事务内完成版本切换与向量写入。

## 4. 端到端主干

一次带记忆的问答与随后的记忆更新构成主干链路：

```
用户提问
  -> api: POST /v1/conversations/{conversationId}/messages (SSE)
  -> application: AnswerWithMemoryUseCase
       -> RetrieveMemoryUseCase
            -> EmbeddingModelPort  (查询向量)
            -> MemorySearchPort    (pgvector 语义候选 + 关键词候选)
            -> domain: HybridScorer (语义/关键词/时间/重要性/置信度加权)
            -> 产出 RetrievalTrace（含每条候选的分量得分与入选原因）
       -> 组装 Prompt（系统提示 + 命中记忆 + 近期会话）
       -> ChatModelPort (流式)
  -> api: 推送 retrieval-trace 事件，随后推送 token 事件，最后 message-completed
  -> application: 落库 message、RetrievalTrace，并登记 memory_task(EXTRACT)

异步续接
  -> MemoryWriteWorker 取出 memory_task
       -> ChatModelPort 结构化输出候选记忆
       -> Bean Validation 校验（失败即拒绝，不写入）
       -> domain: ConflictDetector 与既有当前版本比对
       -> 事务: 旧版本置为 SUPERSEDED + 新 MemoryVersion + memory_embedding + ConflictRecord
  -> 前端记忆中心可见新版本与冲突结果
```

这条主干在 Issue #4 以纵向切片打通，每个 Port 都先有 Fake 实现，保证默认测试不访问网络。

## 5. 关键设计约束

1. **可解释性优先。** 检索不返回“黑箱 Top-K”，每条命中都带分量得分、来源引用和入选原因，`RetrievalTrace` 是一等公民而非日志。
2. **写入不可信。** 模型的结构化输出在校验通过前不进入记忆库；校验失败进入待人工审阅队列。
3. **Owner 隔离。** 所有用户数据表含 `owner_id`，Repository 公开查询必须以当前 Owner 为必填条件。
4. **可替换性。** 模型、存储、检索策略均通过 Port 接入，评测中的基线与 RecallTree 使用同一套接口，保证对比公平。
5. **删除可验证。** 软删除隐藏内容，硬删除清除结构与向量，两者都有可断言的自动化验证。

## 6. 错误与降级策略

| 故障 | 表现 | 处理 |
| --- | --- | --- |
| Provider 超时或限流 | 回答生成失败 | 有限重试后返回明确错误码，记录原始失败原因 |
| 检索失败 | 无法取得记忆 | 降级为无记忆回答，响应标注 `memoryDegraded` |
| 结构化输出不合法 | 候选记忆无效 | 任务标记为 `REJECTED`，进入人工审阅，不污染记忆库 |
| 冲突无法自动判定 | 当前状态不确定 | 保留双版本并标记 `NEEDS_REVIEW`，检索时降权 |
| 数据库不可用 | 写入任务积压 | 任务保持未完成状态并可重放，不丢失待写入记忆 |

## 7. 后续文档

- 领域模型字段与生命周期：[domain-model.md](domain-model.md)
- 接口契约与错误模型：[../api/openapi.yaml](../api/openapi.yaml)
- 资产、信任边界与威胁缓解：[../security/threat-model.md](../security/threat-model.md)
