package com.gearshow.backend.health.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 애플리케이션 상태 확인을 위한 헬스체크 엔드포인트.
 */
@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
