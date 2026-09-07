package com.recalltree.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存活与依赖健康检查（对应 openapi.yaml 的 GET /v1/health）。
 * 首版返回固定 DOWN 状态，后续集成测试会通过 Testcontainers 验证数据库连接。
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
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemBody("Service Unavailable", "All dependencies are currently unavailable"));
    }

    private Map<String, Object> problemBody(String title, String detail) {
        var problem = new LinkedHashMap<String, Object>();
        problem.put("type", URI.create("about:blank"));
        problem.put("title", title);
        problem.put("status", 503);
        problem.put("code", "SERVICE_UNAVAILABLE");
        problem.put("detail", detail);
        return problem;
    }
}
