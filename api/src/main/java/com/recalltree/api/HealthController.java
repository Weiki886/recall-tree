package com.recalltree.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存活与依赖健康检查（对应 openapi.yaml 的 GET /v1/health）。
 * 首版仅返回固定 OK，后续集成测试会通过 Testcontainers 验证数据库连接。
 */
@RestController
public class HealthController {

    @GetMapping("/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        var checks = new LinkedHashMap<String, Object>();
        checks.put("database", "DOWN");
        checks.put("chatProvider", "DISABLED");
        checks.put("embeddingProvider", "DISABLED");
        checks.put("pendingMemoryTasks", 0);

        var body = new LinkedHashMap<String, Object>();
        body.put("status", "DOWN");
        body.put("checks", checks);
        return ResponseEntity.ok(body);
    }
}
