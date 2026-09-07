# ADR-001：采用 Java 模块化单体与 Port/Adapter 边界

## 状态

Accepted

## 日期

2026-09-02

## 背景与问题

RecallTree 需要同时承载长期记忆领域规则、模型调用、PostgreSQL/pgvector、HTTP/SSE API、评测运行器和 Web UI。项目由个人在本科毕业设计周期内持续迭代，既需要明确边界和可测试性，也必须控制部署与运维复杂度。

核心长期记忆方法不能被 Spring AI、数据库 DTO 或 HTTP 框架类型污染，否则难以独立测试、替换 Provider、实现公平基线和解释论文贡献。

## 决策

1. 后端采用 Java 21 LTS、Spring Boot、Spring MVC 和 Maven 多模块构建。
2. 采用模块化单体，不拆分网络微服务。
3. 逻辑模块为：
   - `domain`：聚合、实体、值对象、领域服务与不变量，不依赖 Spring；
   - `application`：用例、事务意图、输入/输出 Port，不依赖具体适配器；
   - `infrastructure`：PostgreSQL、pgvector、Spring AI、时钟、ID 与可观测性适配器；
   - `api`：Spring Boot 组合根、REST/SSE、认证上下文与 DTO 映射；
   - `evaluation`：基线、数据集、指标与报告的独立组合根；
   - `web`：Vue 3/TypeScript 客户端。
4. 依赖方向固定为 `api/evaluation/infrastructure -> application -> domain`。`domain` 不得反向依赖框架模块。
5. API 与评测模块都是组合根，可以装配 Infrastructure Adapter；两者之间不得互相依赖。「不得互相依赖」包含不得复用对方的 DTO、映射器与配置类：评测若复用 `api` 的 DTO，实验结果的形态就会被 HTTP 传输结构绑定，日后调整响应字段会连带影响历史实验的可比性。评测需要的数据一律经 `application` Port 获取，必要时在 `evaluation` 内定义自己的结果模型。
6. 使用 ArchUnit 在 Issue #3 后验证关键依赖规则。

Spring Boot 当前官方版本要求至少 Java 17；选择 Java 21 是为了使用成熟 LTS 运行时，同时避免将项目绑定到更新的非必要语言特性。具体 Spring Boot/Spring AI 补丁版本在工程骨架中锁定，而不是写死在架构原则中。

## 备选方案

- Go 模块化单体：部署简单、资源占用低，但当前项目的主要复杂度是领域状态、事务和 AI 适配，不是极限吞吐量。
- 全栈 TypeScript：开发速度快，但核心领域约束、批量实验和长期演进更依赖运行时 Schema 管理。
- Spring Boot 单模块分包：启动成本最低，但很难自动阻止领域层依赖框架和数据库类型。
- 微服务：边界清晰但会引入网络一致性、部署、追踪和版本兼容成本，超出本科毕设范围。

## 后果

- 正面影响：核心规则可用纯 JUnit 测试；模型、存储和 UI 可替换；单进程部署简单；论文可以清晰区分原创方法与框架适配。
- 负面影响 / 风险：Maven 多模块和 DTO 映射增加少量样板代码；模块边界需要持续用测试和 Review 维护。
- 迁移与恢复：若未来某模块需要独立部署，先把 Application Port 提升为版本化网络契约；在出现真实容量或隔离需求前不拆服务。

## 参考资料

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring AI Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
