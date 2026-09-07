package com.recalltree.infrastructure;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.recalltree.application.ClockPort;

/**
 * {@link ClockPort} 的系统时钟实现。
 *
 * <p>这是 Port/Adapter 边界的最小示例：application 声明接口，
 * infrastructure 提供带框架注解的实现，两者职责不混。
 */
@Component
public class SystemClockAdapter implements ClockPort {

    private final Clock clock;

    public SystemClockAdapter() {
        this(Clock.systemUTC());
    }

    SystemClockAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
