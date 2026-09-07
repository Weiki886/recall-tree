package com.recalltree.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RecallTree 后端入口。Spring Boot 组合根，
 * 扫描 infrastructure 与 application 模块以装配所有 Bean。
 *
 * <p>按 ADR-001 决策 5，api 与 evaluation 是两个独立组合根，
 * 两者之间不得互相依赖。评测入口在 evaluation 模块中。
 */
@SpringBootApplication(
        scanBasePackages = {"com.recalltree.api", "com.recalltree.application", "com.recalltree.infrastructure"})
public class RecallTreeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecallTreeApplication.class, args);
    }
}
