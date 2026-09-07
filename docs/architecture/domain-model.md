# RecallTree 领域模型

本文档定义核心聚合的字段、身份、版本关系与生命周期，是 Issue #4 起各实现 Issue 的共同契约。存储决策见 [ADR-002](../adr/0002-postgresql-pgvector.md)。

## 0. 与 API 契约的关系

本文档描述**持久化结构**，[openapi.yaml](../api/openapi.yaml) 描述**传输结构**，两者刻意不要求逐字段一一对应：

- API 可以做**读模型投影**，把跨表派生的值直接放进响应，避免前端 N+1 请求。例如 `MemoryItem` 响应中的 `currentContent`（取自当前版本）、`versionCount`、`hasUnresolvedConflict`、`confidence`（取自当前版本），在持久化层都不是 `memory_item` 表的列。
- API 可以做**反规范化**。例如 `RetrievalCandidate` 响应带 `content` 与 `memoryItemId`，使记忆中心一次请求即可渲染完整检索轨迹；持久化层只存 `memoryVersionId` 与各分量得分。
- 反过来，持久化字段也可以不出现在 API 中。例如 `MemoryVersion.contentHash`、`MemoryEmbedding` 的全部字段属于内部实现，不对外暴露。

因此实现时的规则是：**枚举值与业务不变量必须严格一致**，字段集合允许 API 侧更丰富。若发现枚举值或不变量在两份文档间不一致，那是缺陷，须以本文档为准修正契约。

## 1. 概念地图

```
Owner
 └── Conversation ── Message
                        │ derivedFrom
                        v
                    SourceRef
                        ^
                        │ evidence
MemoryItem (身份稳定) ──< MemoryVersion (仅追加)
     │                        │
     │                        └── MemoryEmbedding (每版本每模型每修订一条)
     ├──< ConflictRecord (涉及两个及以上版本)
     └──< ReviewDecision (人工覆盖)

RetrievalTrace ──< RetrievalCandidate ──> MemoryVersion
MemoryTask (异步写入流水线的驱动记录)
```

核心区分：`MemoryItem` 是长期稳定的身份，`MemoryVersion` 是不可变的事实快照。所有“记忆被修改”实际都是新增版本并切换当前指针，历史永不原地覆写。

## 2. 会话侧实体

`Conversation` 与 `Message` 是记忆的输入来源，本身不承载记忆规则，此处给出字段以保证本文档可独立作为实现契约。

### Conversation

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 身份 |
| `ownerId` | UUID | 数据归属，所有查询必填条件 |
| `title` | string(≤200) | 会话标题，可由首条消息生成 |
| `messageCount` | int | 消息数量 |
| `lastMessageAt` | timestamptz? | 最近消息时间，用于列表排序 |
| `deletedAt` | timestamptz? | 软删除标记；非空即退出列表与检索上下文 |
| `createdAt` | timestamptz | 创建时间 |

### Message

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 身份，同时作为 `SourceRef.messageId` 的引用目标 |
| `conversationId` | UUID | 所属会话 |
| `ownerId` | UUID | 数据归属 |
| `role` | enum | `USER` / `ASSISTANT` |
| `content` | text | 消息正文 |
| `traceId` | UUID? | 关联的 `RetrievalTrace`，仅 `ASSISTANT` 消息有值 |
| `memoryDegraded` | boolean | 本次回答是否在检索降级下生成 |
| `promptTokens` / `completionTokens` | int? | Token 用量，仅 `ASSISTANT` 消息有值 |
| `model` | string? | 实际使用的模型标识，仅 `ASSISTANT` 消息有值 |
| `latencyMs` | int? | 生成耗时 |
| `createdAt` | timestamptz | 时间 |

不变量：

1. `Message` 仅追加，不可编辑或原地覆写。
2. `SourceRef.messageId` 引用的消息不得被物理删除，否则记忆将失去证据链；会话删除采用软删除。
3. `traceId`、`promptTokens`、`completionTokens`、`model` 只在 `role = ASSISTANT` 时非空。
4. 硬删除记忆不删除来源消息；反之删除会话也不自动删除已提取的记忆，两者生命周期独立。

第 4 条是刻意设计：记忆一旦提取就具有独立价值，不应因为用户清理聊天记录而丢失；反过来用户删除某条记忆时，原始对话仍然保留。若用户要求同时清除两者，需分别调用会话删除与记忆硬删除。

## 3. MemoryItem（聚合根）

代表一条持续存在的记忆主体，例如“用户的毕业设计题目”。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 稳定身份，跨版本不变 |
| `ownerId` | UUID | 数据归属，所有查询必填条件 |
| `type` | enum | `FACT` / `PREFERENCE` / `GOAL` / `TASK` / `CONSTRAINT` / `SKILL_STATE` |
| `subject` | string(≤128) | 记忆主体的规范化标识，用于聚类与冲突候选定位 |
| `attribute` | string(≤128) | 主体的具体属性，`(ownerId, type, subject, attribute)` 构成语义去重键 |
| `currentVersionId` | UUID? | 当前生效版本；`NEEDS_REVIEW` 冲突未决时可为空 |
| `status` | enum | `ACTIVE` / `DORMANT` / `ARCHIVED` / `SOFT_DELETED` / `PURGED` |
| `importance` | decimal(3,2) | 0–1，影响检索权重与遗忘顺序 |
| `accessCount` | int | 被检索命中次数，用于时效评估 |
| `lastAccessedAt` | timestamptz? | 最近命中时间 |
| `createdAt` / `updatedAt` | timestamptz | 审计时间 |

不变量：

1. `(ownerId, type, subject, attribute)` 在未删除记录中唯一；重复提取必须落到同一 `MemoryItem` 上形成新版本。
2. `currentVersionId` 若非空，必须指向同一 `MemoryItem` 且状态为 `CURRENT` 的版本。
3. `status = PURGED` 时不得存在任何版本内容与向量。

### 状态机

```
ACTIVE ──(长期未命中 + 置信度衰减)──> DORMANT ──(继续无命中)──> ARCHIVED
   │                                    │
   └──────────(再次命中)─────────────────┘
ACTIVE / DORMANT / ARCHIVED ──(用户删除)──> SOFT_DELETED ──(确认硬删除)──> PURGED
```

`DORMANT` 仍可被检索但显著降权；`ARCHIVED` 默认不进入检索，仅在记忆中心可见；`SOFT_DELETED` 不进入检索且内容不返回；`PURGED` 是终态且不可恢复。

## 4. MemoryVersion（不可变快照）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 版本身份 |
| `memoryItemId` | UUID | 所属 `MemoryItem` |
| `versionNo` | int | 同一 Item 内自增，从 1 开始 |
| `content` | text | 规范化后的记忆陈述 |
| `contentHash` | char(64) | `content` 的 SHA-256，用于幂等与向量复用 |
| `structured` | jsonb | 类型化字段，如 `{"deadline":"2026-05-20"}` |
| `state` | enum | `CURRENT` / `SUPERSEDED` / `REJECTED` / `NEEDS_REVIEW` |
| `confidence` | decimal(3,2) | 0–1，写入时由证据强度决定，随时间衰减 |
| `validFrom` | timestamptz | 事实开始生效的时间（事件时间，非写入时间） |
| `validTo` | timestamptz? | 事实失效时间；`null` 表示仍然有效 |
| `recordedAt` | timestamptz | 系统写入时间（决策时间） |
| `supersedesVersionId` | UUID? | 被本版本取代的上一版本 |
| `origin` | enum | `MODEL_EXTRACTED` / `USER_ASSERTED` / `USER_CORRECTED` / `SYSTEM_DERIVED` |
| `extractionModel` | string? | 产生该版本的模型标识，`USER_*` 来源为空 |
| `promptVersion` | string? | 提取所用 Prompt 版本，实验可追溯 |

双时间线是刻意设计：`validFrom/validTo` 描述事实何时为真，`recordedAt` 描述系统何时知道。二者分离才能回答“在某个时间点，系统认为当前状态是什么”，也是评测中时间一致性指标的基础。

不变量：

1. 版本行仅追加，除 `state`、`validTo`、`confidence` 外不得更新。
2. 每个 `MemoryItem` 至多一个 `CURRENT` 版本（数据库局部唯一索引保证）。
3. `validTo` 非空时必须晚于 `validFrom`。
4. `supersedesVersionId` 必须属于同一 `MemoryItem` 且 `versionNo` 更小。
5. 置信度锁定有两个来源，锁定后置信度固定为 1.0 且不参与自动衰减：`origin = USER_CORRECTED` 的版本自动锁定；经 `CONFIRM` 审阅的版本由 `ReviewDecision.confidenceLocked` 标记锁定，此时 `origin` 保持原值不变（见第 9 节）。衰减任务必须依据锁定标记判断，不得仅凭 `origin` 推断。

## 5. SourceRef（来源追踪）

每个版本必须至少有一条来源，否则不可进入 `CURRENT`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 身份 |
| `memoryVersionId` | UUID | 所属版本 |
| `kind` | enum | `MESSAGE` / `DOCUMENT` / `USER_INPUT` / `DERIVED` |
| `conversationId` | UUID? | 会话来源 |
| `messageId` | UUID? | 精确消息来源 |
| `quote` | text? | 支撑该记忆的原文片段（≤512 字） |
| `charStart` / `charEnd` | int? | 片段在原消息中的偏移，用于前端高亮 |
| `createdAt` | timestamptz | 记录时间 |

`quote` 让“为什么系统认为这件事成立”可以在 UI 上直接回溯到原始对话，这是论文可解释性论证的证据链末端。

## 6. ConflictRecord（冲突与消解）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 身份 |
| `memoryItemId` | UUID | 冲突所属主体 |
| `incomingVersionId` | UUID | 新提取版本 |
| `existingVersionId` | UUID | 冲突的既有版本 |
| `type` | enum | `CONTRADICTION` / `UPDATE` / `REFINEMENT` / `DUPLICATE` |
| `resolution` | enum | `INCOMING_WINS` / `EXISTING_WINS` / `MERGED` / `BOTH_KEPT` / `NEEDS_REVIEW` |
| `strategy` | enum | `RECENCY` / `SPECIFICITY` / `USER_AUTHORITY` / `MODEL_JUDGED` / `MANUAL` |
| `rationale` | text | 判定理由，面向用户可读 |
| `similarity` | decimal(4,3) | 两版本语义相似度 |
| `decidedAt` | timestamptz | 判定时间 |

消解优先级固定为：`USER_AUTHORITY` > `SPECIFICITY` > `RECENCY` > `MODEL_JUDGED`。用户明确修正过的事实不会被后续模型提取悄悄推翻，这是系统可信度的底线。无法判定时写入 `NEEDS_REVIEW`，双版本保留且检索降权。

## 7. MemoryEmbedding

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 身份 |
| `memoryVersionId` | UUID | 所属版本 |
| `modelId` / `modelRevision` | string | 生成向量的模型与修订 |
| `dimensions` | int | 向量维度 |
| `embedding` | vector(N) | pgvector 列，维度由迁移固定 |
| `contentHash` | char(64) | 与版本内容一致性校验 |
| `createdAt` | timestamptz | 生成时间 |

约束：`(memoryVersionId, modelId, modelRevision)` 唯一。切换模型时新增行而非覆盖，回填完成并校验后再清理旧向量。

## 8. RetrievalTrace 与 RetrievalCandidate

`RetrievalTrace` 记录一次检索的完整决策过程，是可解释检索的持久化产物。

| RetrievalTrace 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 身份 |
| `ownerId` | UUID | 归属 |
| `conversationId` / `messageId` | UUID | 触发检索的上下文 |
| `queryText` | text | 检索查询 |
| `strategy` | string | 检索策略标识（含权重配置版本） |
| `weights` | jsonb | 本次融合实际使用的各分量权重，例如 `{"semantic":0.5,"keyword":0.2,"recency":0.15,"importance":0.1,"confidence":0.05}` |
| `candidateCount` / `selectedCount` | int | 召回与最终注入数量 |
| `latencyMs` | int | 检索耗时 |
| `degraded` | boolean | 是否发生降级 |
| `createdAt` | timestamptz | 时间 |

| RetrievalCandidate 字段 | 类型 | 说明 |
| --- | --- | --- |
| `traceId` | UUID | 所属 trace |
| `memoryVersionId` | UUID | 候选版本 |
| `semanticScore` | decimal(5,4) | 向量相似度分量 |
| `keywordScore` | decimal(5,4) | 关键词匹配分量 |
| `recencyScore` | decimal(5,4) | 时间新鲜度分量 |
| `importanceScore` | decimal(5,4) | 重要性分量 |
| `confidenceScore` | decimal(5,4) | 置信度分量 |
| `finalScore` | decimal(5,4) | 融合总分 |
| `selected` | boolean | 是否注入 Prompt |
| `reason` | string | 入选或落选原因 |

每个分量分数与融合权重全部持久化，因此消融实验可以直接基于真实 trace 分析各信号贡献或替换权重公式重新排名，无需重跑模型。

## 9. ReviewDecision（人工覆盖）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 身份 |
| `memoryItemId` | UUID | 目标记忆 |
| `targetVersionId` | UUID? | 被审阅版本 |
| `action` | enum | `CONFIRM` / `CORRECT` / `MERGE` / `DISCARD` / `RESTORE` |
| `resultVersionId` | UUID? | `CORRECT`/`MERGE` 产生的新版本 |
| `confidenceLocked` | boolean | 该决策是否锁定了目标版本的置信度，使其退出自动衰减 |
| `note` | text? | 用户备注 |
| `decidedAt` | timestamptz | 时间 |

人工决策本身也是仅追加记录。各动作的语义：

| action | 前置状态 | 对版本的影响 | 对 MemoryItem 的影响 |
| --- | --- | --- | --- |
| `CONFIRM` | 目标版本为 `CURRENT` 或 `NEEDS_REVIEW` | 不创建新版本，不改 `origin`；置信度置为 1.0 并锁定（`confidenceLocked = true`）；`NEEDS_REVIEW` 转为 `CURRENT` | 保持或恢复 `ACTIVE` |
| `CORRECT` | 任意未删除版本 | 创建 `origin = USER_CORRECTED` 新版本，旧版本置 `SUPERSEDED` | 切换 `currentVersionId` |
| `MERGE` | 存在两个及以上冲突版本 | 创建 `origin = USER_CORRECTED` 新版本合并内容，被合并版本置 `SUPERSEDED` | 切换 `currentVersionId`，冲突记录标记 `MERGED` |
| `DISCARD` | 目标版本为 `NEEDS_REVIEW` 或非当前版本 | 目标版本置 `REJECTED`，不参与检索 | 若被丢弃版本原为候选当前版本，则 `currentVersionId` 回退至最近的有效 `CURRENT`；若无有效版本则置空并标记待审阅 |
| `RESTORE` | `MemoryItem` 为 `SOFT_DELETED` | 版本状态不变 | 恢复为 `ACTIVE`；`PURGED` 不可恢复 |

`CORRECT` 不修改原版本，而是创建新版本并切换当前指针，保证「系统曾经记错过什么」可被审计。

关于置信度锁定：`CONFIRM` 作用于 `origin = MODEL_EXTRACTED` 的版本时，会出现「置信度为 1.0 且不衰减，但 `origin` 仍是 `MODEL_EXTRACTED`」的状态。这是刻意允许的——用户确认了模型的判断，但没有改写内容，因此不应伪造成 `USER_CORRECTED`。判断是否参与衰减以 `confidenceLocked` 为准，而非以 `origin` 推断（见不变量 5）。

## 10. MemoryTask（异步写入驱动）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUIDv7 | 身份 |
| `ownerId` | UUID | 归属 |
| `kind` | enum | `EXTRACT` / `EMBED` / `RESOLVE_CONFLICT` / `DECAY` / `REEMBED` |
| `status` | enum | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `REJECTED` |
| `payload` | jsonb | 任务输入 |
| `idempotencyKey` | string | 唯一键，重复登记不产生重复写入 |
| `attempts` | int | 已尝试次数 |
| `lastError` | text? | 最近失败原因 |
| `availableAt` | timestamptz | 最早可执行时间，支持退避重试 |

各任务类型的职责：

| kind | 触发方式 | 职责 |
| --- | --- | --- |
| `EXTRACT` | 回答返回后登记 | 从消息中提取候选记忆并校验 |
| `EMBED` | 新版本进入 `CURRENT` 后登记 | 为版本生成向量 |
| `RESOLVE_CONFLICT` | 提取发现冲突时登记 | 执行冲突消解策略 |
| `DECAY` | 定时任务 | 按半衰期更新置信度与状态，不删除内容 |
| `REEMBED` | 切换 Embedding 模型时批量登记 | 用新模型回填向量，对应 [ADR-002](../adr/0002-postgresql-pgvector.md) 决策 5 的双写/回填/校验/切换流程 |

**硬删除不是任务类型。** 它必须在单个同步事务内完成，否则会出现"结构已删、向量残留"的中间状态，无法满足威胁模型验收项 2（硬删除后三张表均无残留）。因此 `purge` 只有同步用例，不进入 `memory_task`。

`EXTRACT` 的 `idempotencyKey` 由 `(conversationId, messageId, promptVersion)` 派生，因此同一条消息不会被重复提取成多条记忆。

## 11. 时效与遗忘

置信度随时间衰减，按记忆类型使用不同半衰期：

| 类型 | 半衰期 | 理由 |
| --- | --- | --- |
| `CONSTRAINT` | 不衰减 | 硬性约束在被显式解除前一直有效 |
| `FACT` | 长（约 365 天） | 稳定属性变化缓慢 |
| `PREFERENCE` | 中（约 180 天） | 偏好会漂移 |
| `SKILL_STATE` | 中短（约 90 天） | 能力状态持续变化 |
| `GOAL` | 短（约 60 天） | 目标随进度更替 |
| `TASK` | 很短（约 14 天） | 任务通常快速完成或过期 |

每次被检索命中会提升有效新鲜度（`lastAccessedAt` 与 `accessCount` 参与评分），实现“常用记忆更不易被遗忘”。衰减只改变状态与权重，绝不静默删除内容；删除只能由用户显式触发。

## 12. 删除语义

| 操作 | 效果 | 可恢复 |
| --- | --- | --- |
| 软删除 | `status = SOFT_DELETED`，退出检索，内容不再返回 | 是 |
| 硬删除 | 事务内删除版本内容、结构化字段、来源引用与向量，`MemoryItem` 置 `PURGED` 并保留无内容审计壳 | 否 |

硬删除必须可自动验证：删除后按 `memoryItemId` 查询版本、向量、来源均返回空，且相关 `RetrievalCandidate` 不再解析出内容。审计壳只保留 `id`、`ownerId`、`purgedAt`，不含任何用户文本。
