package com.team03.ticketmon._global.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HealthController — ALB/로드밸런서용 헬스체크 엔드포인트
 *
 * GET /health 호출에 대해 단순 문자열("ALB HEALTHY")을 200으로 반환합니다.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("ALB HEALTHY");
    }
}
