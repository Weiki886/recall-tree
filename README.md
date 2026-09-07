# RecallTree

RecallTree 是一个面向 AI Agent 的长期记忆管理系统，重点研究记忆版本、冲突消解、时效控制、可解释检索与用户数据控制。

论文题目：**智能体长期记忆管理系统的设计与实现**。

## 当前状态

项目处于 `v0.1.0 - 工程与架构基础` 阶段。当前只建立已评审的产品和架构约束，尚未提供可运行应用。

- Proposal：[#1](https://github.com/Weiki886/recall-tree/issues/1)
- 当前工作项：[#2](https://github.com/Weiki886/recall-tree/issues/2)
- 总体架构：[docs/architecture/overview.md](docs/architecture/overview.md)
- 领域模型：[docs/architecture/domain-model.md](docs/architecture/domain-model.md)
- API 契约：[docs/api/openapi.yaml](docs/api/openapi.yaml)
- 威胁模型：[docs/security/threat-model.md](docs/security/threat-model.md)
- 架构决策：[docs/adr](docs/adr)

## 已确认技术路线

- Java 21 LTS、Spring Boot、Spring MVC、Maven；
- 模块化单体与 Port/Adapter 边界；
- PostgreSQL 17、pgvector、MyBatis、Flyway；
- Spring AI 只用于 Chat/Embedding Provider 适配；
- Vue 3、TypeScript、Vite；
- JUnit 5、Testcontainers、WireMock、ArchUnit、Vitest、Playwright；
- Docker Compose 与 GitHub Actions。

详细取舍以 ADR 为准。依赖版本在工程骨架 Issue #3 中通过 Maven BOM、Wrapper 和锁定文件固定。

## 项目边界

RecallTree 不训练基础大模型，也不以复刻通用 Claude/Codex 平台为目标。它以可替换模型之上的长期记忆能力为核心，并通过个人学习与毕业设计助手验证效果。

公开仓库不得提交真实个人隐私、API Key、受限论文数据或未脱敏模型日志。
