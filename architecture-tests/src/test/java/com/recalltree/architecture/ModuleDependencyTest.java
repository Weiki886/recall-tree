package com.recalltree.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 断言 ADR-001 定义的依赖方向：
 * {@code api / evaluation / infrastructure -> application -> domain}。
 *
 * <p>文档中的边界约定只是声明，只有在 CI 中断言才真正生效。
 * 这是 Issue #2 验收标准中「由 ArchUnit 断言」的落地。
 *
 * <p>注意所有规则都不使用 {@code allowEmptyShould(true)}：
 * 若某条规则匹配不到任何类，ArchUnit 会直接失败而不是静默通过。
 * 这一点很重要——空跑的架构规则比没有规则更危险，因为它给出虚假的安全感。
 */
class ModuleDependencyTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.recalltree");
    }

    @Test
    @DisplayName("前置检查：五个模块的类都已加载，否则后续规则会空跑")
    void allModulesArePresentOnClasspath() {
        assertThat(classes).isNotEmpty();
        for (String pkg : new String[] {
            "com.recalltree.domain",
            "com.recalltree.application",
            "com.recalltree.infrastructure",
            "com.recalltree.api",
            "com.recalltree.evaluation"
        }) {
            assertThat(classes.stream().anyMatch(c -> c.getPackageName().startsWith(pkg)))
                    .as("模块 %s 必须在架构测试的 classpath 上，" + "否则针对它的规则会匹配不到任何类而失去保护作用", pkg)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("domain 不得依赖任何其他内部模块")
    void domainMustNotDependOnOtherModules() {
        noClasses()
                .that()
                .resideInAPackage("com.recalltree.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.recalltree.application..",
                        "com.recalltree.infrastructure..",
                        "com.recalltree.api..",
                        "com.recalltree.evaluation..")
                .because("ADR-001：依赖方向为 api/evaluation/infrastructure -> application -> domain")
                .check(classes);
    }

    @Test
    @DisplayName("domain 不得依赖 Spring、MyBatis、JDBC 或 Servlet")
    void domainMustNotDependOnFrameworks() {
        noClasses()
                .that()
                .resideInAPackage("com.recalltree.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "java.sql..",
                        "javax.sql..",
                        "org.apache.ibatis..",
                        "org.mybatis..")
                .because("ADR-001：核心记忆方法必须能用纯 JUnit 验证，不被框架类型污染")
                .check(classes);
    }

    @Test
    @DisplayName("application 不得依赖具体 Adapter 或组合根")
    void applicationMustNotDependOnAdapters() {
        noClasses()
                .that()
                .resideInAPackage("com.recalltree.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.recalltree.infrastructure..", "com.recalltree.api..", "com.recalltree.evaluation..")
                .because("ADR-001：application 只定义 Port，由 infrastructure 实现")
                .check(classes);
    }

    @Test
    @DisplayName("application 不得直接使用 MyBatis 或 JDBC")
    void applicationMustNotUsePersistenceApis() {
        noClasses()
                .that()
                .resideInAPackage("com.recalltree.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.apache.ibatis..", "org.mybatis..", "java.sql..", "javax.sql..")
                .because("ADR-002 决策 10：SQL 集中在 infrastructure 的 XML Mapper 中")
                .check(classes);
    }

    @Test
    @DisplayName("evaluation 不得依赖 api 模块（含其 DTO）")
    void evaluationMustNotDependOnApi() {
        noClasses()
                .that()
                .resideInAPackage("com.recalltree.evaluation..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.recalltree.api..")
                .because("ADR-001 决策 5：复用 api DTO 会把实验结果绑定到 HTTP 传输结构，" + "日后调整响应字段会破坏历史实验的可比性")
                .check(classes);
    }

    @Test
    @DisplayName("api 不得依赖 evaluation 模块")
    void apiMustNotDependOnEvaluation() {
        noClasses()
                .that()
                .resideInAPackage("com.recalltree.api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.recalltree.evaluation..")
                .because("ADR-001 决策 5：api 与 evaluation 是两个独立组合根，不得互相依赖")
                .check(classes);
    }

    @Test
    @DisplayName("infrastructure 不得依赖组合根")
    void infrastructureMustNotDependOnCompositionRoots() {
        noClasses()
                .that()
                .resideInAPackage("com.recalltree.infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.recalltree.api..", "com.recalltree.evaluation..")
                .because("ADR-001：infrastructure 实现 Port，由组合根装配，不反向依赖")
                .check(classes);
    }
}
