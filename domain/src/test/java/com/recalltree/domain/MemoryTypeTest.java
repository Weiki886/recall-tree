package com.recalltree.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 验证记忆类型的衰减规则（领域模型第 11 节）。
 *
 * <p>这个测试同时证明 domain 模块可以在没有 Spring 上下文的情况下测试，
 * 这是 ADR-001 选择模块化单体的主要动机。
 */
class MemoryTypeTest {

    @Test
    @DisplayName("CONSTRAINT 不参与自动衰减")
    void constraintDoesNotDecay() {
        assertThat(MemoryType.CONSTRAINT.decays()).isFalse();
        assertThat(MemoryType.CONSTRAINT.halfLife()).isNull();
    }

    @Test
    @DisplayName("除 CONSTRAINT 外所有类型都参与衰减")
    void allOtherTypesDecay() {
        for (MemoryType type : MemoryType.values()) {
            if (type == MemoryType.CONSTRAINT) {
                continue;
            }
            assertThat(type.decays()).as("%s 应参与衰减", type).isTrue();
            assertThat(type.halfLife()).as("%s 的半衰期应为正值", type).isNotNull().isPositive();
        }
    }

    @Test
    @DisplayName("半衰期递减顺序：FACT > PREFERENCE > SKILL_STATE > GOAL > TASK")
    void halfLifeOrderReflectsVolatility() {
        assertThat(MemoryType.FACT.halfLife()).isGreaterThan(MemoryType.PREFERENCE.halfLife());
        assertThat(MemoryType.PREFERENCE.halfLife()).isGreaterThan(MemoryType.SKILL_STATE.halfLife());
        assertThat(MemoryType.SKILL_STATE.halfLife()).isGreaterThan(MemoryType.GOAL.halfLife());
        assertThat(MemoryType.GOAL.halfLife()).isGreaterThan(MemoryType.TASK.halfLife());
    }

    @Test
    @DisplayName("半衰期与领域模型文档中的数值一致")
    void halfLifeMatchesDomainModelDoc() {
        assertThat(MemoryType.FACT.halfLife()).isEqualTo(Duration.ofDays(365));
        assertThat(MemoryType.PREFERENCE.halfLife()).isEqualTo(Duration.ofDays(180));
        assertThat(MemoryType.SKILL_STATE.halfLife()).isEqualTo(Duration.ofDays(90));
        assertThat(MemoryType.GOAL.halfLife()).isEqualTo(Duration.ofDays(60));
        assertThat(MemoryType.TASK.halfLife()).isEqualTo(Duration.ofDays(14));
    }
}
