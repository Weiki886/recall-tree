package com.recalltree.domain;

import java.time.Duration;

/**
 * 记忆类型。每种类型的置信度半衰期不同（见领域模型第 11 节）。
 *
 * <p>半衰期定义在 domain 层而非配置文件中，因为它是领域规则的一部分：
 * 「任务比事实更快过期」是本项目的核心假设之一，需要可用纯单元测试验证。
 */
public enum MemoryType {

    /** 硬性约束，在被显式解除前一直有效，不衰减。 */
    CONSTRAINT(null),

    /** 稳定属性，变化缓慢。 */
    FACT(Duration.ofDays(365)),

    /** 偏好会随时间漂移。 */
    PREFERENCE(Duration.ofDays(180)),

    /** 能力状态持续变化。 */
    SKILL_STATE(Duration.ofDays(90)),

    /** 目标随进度更替。 */
    GOAL(Duration.ofDays(60)),

    /** 任务通常快速完成或过期。 */
    TASK(Duration.ofDays(14));

    private final Duration halfLife;

    MemoryType(Duration halfLife) {
        this.halfLife = halfLife;
    }

    /**
     * 返回该类型的置信度半衰期。
     *
     * @return 半衰期；{@code CONSTRAINT} 返回 null 表示不衰减
     */
    public Duration halfLife() {
        return halfLife;
    }

    /** 该类型是否参与自动衰减。 */
    public boolean decays() {
        return halfLife != null;
    }
}
