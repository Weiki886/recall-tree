# ADR-002：采用 PostgreSQL、pgvector 与显式 SQL 数据访问

## 状态

Accepted

## 日期

2026-09-02

## 背景与问题

长期记忆不仅包含文本和向量，还包含版本、来源、冲突、有效时间、人工覆盖、删除状态和检索轨迹。一次记忆更新可能同时创建新版本、终止旧版本、保存冲突结果并更新向量。结构数据与向量若分属不同系统，首版就需要处理跨存储一致性和补偿。

混合检索还需要公开语义、关键词、时间、重要性与置信度等评分分量，不能把排序完全隐藏在通用 VectorStore 抽象中。

## 决策

1. 首版使用 PostgreSQL 17 作为唯一持久化数据库，并启用 pgvector 扩展。
2. 结构化记忆、会话、版本、来源、冲突、任务和检索轨迹均存入 PostgreSQL；向量保存在独立 `memory_embedding` 表。
3. 使用 Flyway 管理所有 Schema 变化；应用启动不得隐式创建或修改生产 Schema。
4. 使用 Spring JDBC（优先 `NamedParameterJdbcTemplate`）和 pgvector-java 执行显式 SQL，不使用 JPA 作为核心检索抽象。
5. 向量维度在选定首个 Embedding Model 后由迁移固定为 `vector(N)`；切换维度时使用影子表/列重新嵌入并切换，不原地误读旧向量。
6. `memory_embedding` 记录 `model_id`、`model_revision`、`dimensions`、`content_hash` 与生成时间，实验必须能追溯实际模型。
7. 语义召回使用 pgvector；关键词候选使用应用预分词字段和 PostgreSQL 全文/精确匹配。候选融合与业务重排由 RecallTree 自己实现。
8. 结构版本切换与同一版本的向量写入在一个数据库事务内完成。外部模型调用不得持有数据库事务。
9. 所有用户数据表包含 `owner_id`，Repository 的公开查询必须以当前 Owner 作为必填条件。

## 备选方案

- PostgreSQL + Milvus：大规模向量检索能力更强，但需要跨存储一致性、额外部署和删除补偿，当前规模没有收益。
- MongoDB：文档结构灵活，但版本关系、唯一约束、事务化状态切换和混合 SQL 分析不如 PostgreSQL 直接。
- Neo4j：适合复杂图遍历，但本项目首版关系以版本链和来源引用为主，尚无必须使用图数据库的查询。
- JPA/Hibernate：普通 CRUD 方便，但自定义 pgvector、排名融合、局部索引和实验 SQL 更难保持透明。
- jOOQ：类型安全优秀，可在查询规模显著增长后重新评估；首版选择 Spring JDBC 以降低代码生成与构建复杂度。

## 后果

- 正面影响：事务边界清楚；彻底删除更容易验证；备份和本地部署简单；评分 SQL 可解释、可测试。
- 负面影响 / 风险：单库最终存在容量上限；中文关键词检索需要应用分词；切换 Embedding 维度需要迁移和重新嵌入。
- 迁移与恢复：所有迁移需提供前向修复或回滚说明；Embedding 切换采用双写/回填/校验/切换流程，旧向量在验收后再清理。

## 参考资料

- [pgvector](https://github.com/pgvector/pgvector)
- [pgvector-java](https://github.com/pgvector/pgvector-java)
- [PostgreSQL JSON Types](https://www.postgresql.org/docs/current/datatype-json.html)
- [PostgreSQL Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)
