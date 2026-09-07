package com.recalltree.evaluation;

import java.time.Instant;
import java.util.Objects;

/**
 * 一次评测运行的标识与配置快照。
 *
 * <p>评测必须固定模型配置与 Prompt 版本（ADR-003 决策 9），
 * 因此运行标识需要携带策略与模型信息，使结果可追溯到确切配置。
 *
 * <p>本类刻意定义在 evaluation 模块内而非复用 api 的 DTO：
 * 实验结果的形态不应被 HTTP 传输结构绑定（ADR-001 决策 5）。
 */
public record EvaluationRun(String runId, String strategy, String model, String promptVersion, Instant startedAt) {

    public EvaluationRun {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}
