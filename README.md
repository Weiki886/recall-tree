# RecallTree

RecallTree 是一个面向 AI Agent 的长期记忆管理系统，重点研究记忆版本、冲突消解、时效控制、可解释检索与用户数据控制。

论文题目：**智能体长期记忆管理系统的设计与实现**。

## 当前状态

项目处于 `v0.1.0 - 工程与架构基础` 阶段。架构约束已评审，工程骨架已可编译、可测试、可启动，但业务功能尚未实现。

- Proposal：[#1](https://github.com/Weiki886/recall-tree/issues/1)
- 当前工作项：[#3](https://github.com/Weiki886/recall-tree/issues/3)
- 总体架构：[docs/architecture/overview.md](docs/architecture/overview.md)
- 领域模型：[docs/architecture/domain-model.md](docs/architecture/domain-model.md)
- API 契约：[docs/api/openapi.yaml](docs/api/openapi.yaml)
- 威胁模型：[docs/security/threat-model.md](docs/security/threat-model.md)
- 架构决策：[docs/adr](docs/adr)

## 快速开始

### 环境要求

只需要 JDK 21+、Node 22+（当前活跃 LTS）与 Docker。Maven 不需要预装，仓库自带 Wrapper。

```bash
java -version    # 需要 21 或更高
node -version    # 需要 22 或更高
docker --version
```

### 一个命令启动最小服务

```bash
./scripts/dev-up.sh
```

该脚本会依次完成：从 `.env.example` 生成 `.env`（若不存在）、启动 PostgreSQL 17 + pgvector 容器并等待其就绪、构建后端、启动 API。
就绪后访问 <http://localhost:8080/v1/health>。

停止数据库容器：

```bash
docker compose down
```

### 前端开发服务

```bash
cd web
corepack enable          # 首次执行，用于启用 pnpm
pnpm install --frozen-lockfile
pnpm dev                 # http://localhost:5173，/v1 请求代理到后端 8080
```

## 开发命令

后端在仓库根目录执行，前端在 `web/` 下执行。所有命令在检查失败时返回非零退出码。

| 用途 | 后端 | 前端 |
| --- | --- | --- |
| 格式化 | `./mvnw spotless:apply` | `pnpm format` |
| 格式检查 | `./mvnw spotless:check` | `pnpm format:check` |
| 静态检查 | `./mvnw spotless:check`（含未用 import、import 顺序） | `pnpm lint` |
| 类型检查 | 由 `javac` 在编译期完成 | `pnpm typecheck` |
| 测试 | `./mvnw test` | `pnpm test` |
| 完整构建 | `./mvnw clean verify` | `pnpm build` |

`./mvnw clean verify` 会一并执行 ArchUnit 架构测试，校验 ADR-001 规定的模块依赖方向。

### 集成测试

集成测试使用 Testcontainers 动态启动 PostgreSQL 17 + pgvector 容器，不依赖开发者预装数据库。需要 Docker 运行环境。

```bash
./mvnw -Pintegration-tests verify
```

该 profile 激活 `maven-failsafe-plugin`，执行 `**/*IT.java`。默认 `mvn verify` 不依赖 Docker，仅运行单元测试与架构测试。

### 配置与凭据

配置项全部通过环境变量注入，样例见 [.env.example](.env.example)。`.env` 已被 `.gitignore` 排除，真实凭据不得提交（见威胁模型 T9、T16）。

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
