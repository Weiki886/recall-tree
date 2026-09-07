package com.recalltree.application;

import java.time.Instant;

/**
 * 可控时钟。衰减计算与分页依赖时间，必须可在测试中固定。
 *
 * <p>见 overview 的 Port 清单：属于基础设施类 Port，由 infrastructure 实现。
 */
public interface ClockPort {

    Instant now();
}
