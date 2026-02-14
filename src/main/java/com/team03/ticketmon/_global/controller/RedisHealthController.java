package com.team03.ticketmon._global.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 연결 상태 확인용 컨트롤러
 * - Redis가 제대로 연결되어 있는지 테스트하는 API들을 모아놓은 클래스
 * - Redisson이라는 Redis 클라이언트 라이브러리를 사용해서 연결을 테스트함
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class RedisHealthController {

    private final RedissonClient redissonClient;

    /**
     * Redis 연결 상태 확인 API
     * 브라우저에서 GET /health/redis 로 접속하면 실행됨
     * Redis에 값을 저장했다가 다시 읽어와서, 제대로 동작하는지 확인하는 방식
     *
     * @return Redis 연결 성공/실패 메시지가 담긴 Map(JSON 형태)
     */

    @GetMapping("/redis")
    public ResponseEntity<Map<String, Object>> checkRedisConnection() {
        Map<String, Object> response = new HashMap<>();

        try {
            String testKey = "health:test:" + System.currentTimeMillis();
            String testValue = "Redis 연결 테스트 - " + LocalDateTime.now();

            RBucket<String> bucket = redissonClient.getBucket(testKey);
            bucket.set(testValue);

            String retrievedValue = bucket.get();

            if (testValue.equals(retrievedValue)) {
                response.put("status", "SUCCESS");
                response.put("message", "Redis 연결 성공");
                response.put("timestamp", LocalDateTime.now());
                response.put("testKey", testKey);
                response.put("testValue", retrievedValue);

                bucket.delete();

                log.info("Redis 연결 테스트 성공: {}", testKey);
                return ResponseEntity.ok(response);
            } else {
                throw new RuntimeException("저장된 값과 조회된 값이 일치하지 않음");
            }

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "Redis 연결 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis 연결 테스트 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Redis 서버 정보 조회 API
     * 브라우저에서 GET /health/redis/info 로 접속하면 실행됨
     * Redis 서버(Redisson 클라이언트)가 종료되었는지 아닌지를 확인함
     *
     * @return Redis 서버 기본 정보 (종료 여부 등)
     */

    @GetMapping("/redis/info")
    public ResponseEntity<Map<String, Object>> getRedisInfo() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isShutdown = redissonClient.isShutdown();
            boolean isShuttingDown = redissonClient.isShuttingDown();

            response.put("status", "SUCCESS");
            response.put("isShutdown", isShutdown);
            response.put("isShuttingDown", isShuttingDown);
            response.put("timestamp", LocalDateTime.now());

            log.info("Redis 정보 조회 성공");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "FAILURE");
            response.put("message", "Redis 정보 조회 실패: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());

            log.error("Redis 정보 조회 실패", e);
            return ResponseEntity.status(500).body(response);
        }
    }
}