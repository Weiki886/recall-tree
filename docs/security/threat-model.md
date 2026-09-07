# RecallTree 威胁模型

范围为首版单体部署：Vue 前端、Spring Boot 后端、PostgreSQL/pgvector 与外部 Chat/Embedding Provider。目标是让安全约束在写代码前落到具体缓解措施，而不是答辩前补文档。方法上按 STRIDE 检查每条信任边界。

## 1. 敏感资产

| 资产 | 敏感度 | 说明 |
| --- | --- | --- |
| 记忆内容与版本历史 | 高 | 长期积累的个人事实、目标、偏好，泄漏影响远大于单次对话 |
| 会话消息与来源引用 | 高 | 含原文引用，可反推完整对话 |
| 检索轨迹 | 中 | 含记忆内容片段与评分，泄漏等于泄漏记忆 |
| Provider API Key | 高 | 泄漏导致资金损失与配额滥用 |
| 数据库凭据 | 高 | 泄漏等于全量数据泄漏 |
| 用户认证 Token | 高 | 可完全冒充用户 |
| 评测数据集与结果 | 低 | 需保证脱敏，避免真实隐私进入公开仓库 |

记忆数据有一个区别于普通业务数据的特点：它是**跨会话累积**的。单条消息泄漏影响有限，但记忆库泄漏相当于交出用户的长期画像，因此删除能力和 Owner 隔离必须可验证，而非声明。

## 2. 信任边界

```
[浏览器 / Vue]                     不可信：用户可篡改任意请求
      | B1: HTTPS + Bearer Token
      v
[api 模块]                         半可信：需校验一切输入
      | B2: 进程内调用（application/domain）
      v
[application / domain]             可信核心
      |                    \
      | B3: JDBC            | B4: HTTPS 出站
      v                     v
[PostgreSQL]            [Chat/Embedding Provider]
 可信存储                 不可信输出（模型响应视为不可信输入）
```

四条边界的核心风险各不相同：B1 是身份与授权，B3 是 Owner 隔离与注入，B4 既是凭据管理也是不可信输出，B2 内部则依赖 ArchUnit 保证领域层不被框架污染。

## 3. 主要威胁与缓解

### B1 浏览器到 API

| ID | 威胁 | STRIDE | 缓解 |
| --- | --- | --- | --- |
| T1 | 伪造或重放 Token 冒充其他用户 | Spoofing | 短有效期 Bearer Token；服务端只从 Token 解析 ownerId，请求体携带的 ownerId 一律忽略 |
| T2 | 越权读取他人记忆（改 URL 中的 UUID） | Information Disclosure | 所有查询以 ownerId 为必填条件；跨 Owner 命中返回 404 而非 403，避免存在性泄漏 |
| T3 | 恶意超长输入或高频请求耗尽模型配额 | DoS | 请求体大小上限、`content` ≤ 8000 字符、按 Owner 限流并返回 429 + Retry-After |
| T4 | 记忆内容含脚本，在前端渲染时执行 | Tampering | 前端一律按文本渲染，禁用 `v-html`；CSP 限制脚本源 |
| T5 | 误触发不可恢复的硬删除 | Repudiation | 硬删除要求先软删除，且请求体 `confirm` 必须等于记忆 UUID；保留不含内容的审计壳 |

### B3 后端到数据库

| ID | 威胁 | STRIDE | 缓解 |
| --- | --- | --- | --- |
| T6 | SQL 注入 | Tampering | MyBatis 一律使用 `#{}` 预编译绑定；禁止用 `${}` 拼接用户输入，排序字段与方向只允许白名单枚举值；动态过滤用 `<where><if>` 而非字符串拼接 |
| T7 | 遗漏 ownerId 条件导致跨用户数据串门 | Information Disclosure | Repository 公开方法必须接收 ownerId；用集成测试构造两个 Owner 断言互相不可见 |
| T8 | 硬删除后向量或来源残留 | Information Disclosure | 单事务内级联删除版本、来源、向量；删除后用自动化测试断言三张表均查不到记录 |
| T9 | 数据库凭据泄漏 | Information Disclosure | 凭据只来自环境变量；仓库中只有 `.env.example`；`.gitignore` 排除真实配置 |
| T10 | 并发写入破坏“至多一个 CURRENT 版本”不变量 | Tampering | 数据库局部唯一索引作为最终保证，而非仅靠应用层检查 |

### B4 后端到模型 Provider

| ID | 威胁 | STRIDE | 缓解 |
| --- | --- | --- | --- |
| T11 | API Key 进入日志、异常栈或前端响应 | Information Disclosure | Key 只在 Adapter 内使用；日志脱敏；错误响应只返回 `provider_unavailable`，不含上游原文 |
| T12 | 提示注入：用户诱导模型写入虚假记忆或删除既有记忆 | Tampering | 模型输出只能产生"候选记忆"，无权执行删除；删除仅由用户显式 API 触发；用户修正过的记忆受 `USER_AUTHORITY` 保护，不被模型提取推翻 |
| T13 | 结构化输出畸形或字段越界写入记忆库 | Tampering | 反序列化后强制 Bean Validation；失败标记 `REJECTED` 进入人工审阅，不写入记忆 |
| T14 | 记忆内容随 Prompt 外发给第三方 Provider | Information Disclosure | 只注入本次检索命中的记忆而非全库；默认不将敏感 Prompt/响应写入普通日志；支持 Ollama 本地部署作为完全离线选项 |
| T15 | Provider 挂起导致请求堆积、事务长时间占用 | DoS | 显式连接与读取超时、有限重试与退避；数据库事务严禁跨越模型调用 |

### 跨边界与供应链

| ID | 威胁 | STRIDE | 缓解 |
| --- | --- | --- | --- |
| T16 | 真实隐私数据或密钥被提交进公开仓库 | Information Disclosure | 评测数据必须脱敏；CI 加入密钥扫描；PR 检查禁止提交 `.env` |
| T17 | 依赖漏洞 | Elevation of Privilege | Maven BOM 锁定版本；启用 Dependabot 与 CI 依赖检查 |
| T18 | 领域层被框架类型污染，绕过既定边界 | Tampering | ArchUnit 断言依赖方向，违规即 CI 失败 |

## 4. 首版明确不做

这些项超出本科毕业设计范围，记录在此以免被误认为已实现：

- 多租户组织与角色权限模型（首版只有单用户 Owner 隔离）；
- 静态数据加密与密钥轮换（依赖部署环境的磁盘加密）；
- 完整审计日志合规留存与防篡改；
- WAF、DDoS 防护与生产级高可用；
- 记忆内容的差分隐私或匿名化处理。

## 5. 可验证的安全验收项

以下每项都要有对应自动化测试，作为安全声明的证据。「保障机制」列说明该验收项依赖的具体设计，而非仅依赖某条缓解措施：

| # | 验收项 | 对应威胁 | 保障机制 | 落地 Issue |
| --- | --- | --- | --- | --- |
| 1 | 两个 Owner 的记忆互不可见（跨 Owner 请求返回 404） | T1、T2、T7 | 所有表含 `owner_id`；Repository 公开查询以 Owner 为必填条件 | #6 |
| 2 | 硬删除后版本、来源、向量三张表均无残留 | T8 | 单事务级联删除 + 仅保留无内容审计壳（[领域模型第 12 节](../architecture/domain-model.md)） | #9 |
| 3 | 畸形模型输出不产生任何 `CURRENT` 版本 | T13 | 结构化输出强制 Bean Validation，失败置 `REJECTED`（ADR-003 决策 5） | #5 |
| 4 | 提示注入样例无法删除或篡改已有记忆 | T12 | 模型只能产出候选记忆，无删除权限；删除仅由用户显式 API 触发 | #5、#9 |
| 5 | 日志与错误响应中不出现 API Key 或数据库凭据 | T9、T11 | 凭据仅存于环境变量；日志脱敏；错误响应不含上游原文 | #3、#16 |
| 6 | 用户修正过的记忆不被后续模型提取自动覆盖 | T12 | 冲突消解优先级 `USER_AUTHORITY` 最高；`USER_CORRECTED` 版本置信度锁定 1.0 且不参与衰减（[领域模型不变量 5](../architecture/domain-model.md)） | #8 |

验收项 6 的保障主要来自领域模型的冲突消解规则，而非提示注入的输入过滤。因此它的测试应构造「用户修正 → 模型提取相反结论」的序列，断言当前版本仍为用户修正值。
