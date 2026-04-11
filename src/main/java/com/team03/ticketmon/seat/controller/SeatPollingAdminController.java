package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.seat.scheduler.SeatPollingSessionCleanupScheduler;
import com.team03.ticketmon.seat.service.SeatPollingSessionManager;
import com.team03.ticketmon.seat.service.SeatStatusEventPublisher;
import com.team03.ticketmon.seat.service.SeatStatusEventSubscriber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SeatPollingAdminController — 좌석 폴링 시스템 운영/관리 컨트롤러
 *
 * 이 클래스가 하는 일:
 *   1. 폴링 세션/이벤트 발행자/구독자/정리 스케줄러의 상태를 통합 조회하는 대시보드 제공
 *   2. 활성 세션 강제 정리, 이벤트 통계 리셋 등 운영 액션 엔드포인트 제공
 *   3. 장애 진단을 위한 내부 통계(처리 건수, 실패 건수 등) 노출
 *
 * 주의: 관리자 전용 엔드포인트이며, 운영 환경에서는 ADMIN 권한 기반 접근 제어가 필요하다.
 */
@Tag(name = "좌석 폴링 관리자", description = "관리자 전용 좌석 폴링 시스템 관리 API")
@Slf4j
@RestController
@RequestMapping("/api/admin/seat-polling")
@RequiredArgsConstructor
public class SeatPollingAdminController {

    private final SeatPollingSessionManager sessionManager;
    private final SeatStatusEventSubscriber eventSubscriber;
    private final SeatStatusEventPublisher eventPublisher;
    private final SeatPollingSessionCleanupScheduler cleanupScheduler;

    /**
     * 전체 시스템 대시보드 정보 조회
     */
    @Operation(summary = "폴링 시스템 대시보드",
            description = "전체 폴링 시스템의 상태를 한눈에 볼 수 있는 대시보드 정보")
    @GetMapping("/dashboard")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getDashboard() {
        try {
            Map<String, Object> dashboard = Map.of(
                    "sessionManager", sessionManager.getSystemStatus(),
                    "eventSubscriber", eventSubscriber.getSubscriberStats(),
                    "eventPublisher", eventPublisher.getPublisherStats(),
                    "scheduler", cleanupScheduler.getSchedulerStatus(),
                    "systemHealth", calculateSystemHealth()
            );

            return ResponseEntity.ok(SuccessResponse.of("대시보드 조회 성공", dashboard));

        } catch (Exception e) {
            log.error("대시보드 조회 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("대시보드 조회 실패", null));
        }
    }

    /**
     * 특정 콘서트의 폴링 세션 상세 정보
     */
    @Operation(summary = "콘서트별 폴링 세션 상세", description = "특정 콘서트의 활성 세션 정보")
    @GetMapping("/concerts/{concertId}/sessions")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getConcertSessions(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            Map<String, Object> sessionInfo = Map.of(
                    "concertId", concertId,
                    "activeSessionCount", sessionManager.getSessionCount(concertId),
                    "maxSessionsPerConcert", 1000, // TODO: 설정에서 가져오기
                    "loadPercentage", calculateLoadPercentage(concertId),
                    "channelName", eventPublisher.getChannelName(concertId)
            );

            return ResponseEntity.ok(SuccessResponse.of("콘서트 세션 정보 조회 성공", sessionInfo));

        } catch (Exception e) {
            log.error("콘서트 세션 정보 조회 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("콘서트 세션 정보 조회 실패", null));
        }
    }

    /**
     * 수동 세션 정리 실행
     */
    @Operation(summary = "수동 세션 정리", description = "만료된 폴링 세션들을 즉시 정리")
    @PostMapping("/cleanup/sessions")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> triggerSessionCleanup() {
        try {
            int sessionsBefore = sessionManager.getTotalSessionCount();

            // 수동 정리 실행
            cleanupScheduler.triggerManualCleanup();

            int sessionsAfter = sessionManager.getTotalSessionCount();
            int cleanedCount = sessionsBefore - sessionsAfter;

            Map<String, Object> result = Map.of(
                    "sessionsBefore", sessionsBefore,
                    "sessionsAfter", sessionsAfter,
                    "cleanedCount", cleanedCount,
                    "cleanupTime", java.time.LocalDateTime.now()
            );

            log.info("관리자 수동 세션 정리 실행: {}", result);
            return ResponseEntity.ok(SuccessResponse.of("세션 정리 완료", result));

        } catch (Exception e) {
            log.error("수동 세션 정리 실행 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("세션 정리 실패", null));
        }
    }

    /**
     * Redis 구독자 재시작
     */
    @Operation(summary = "Redis 구독자 재시작", description = "Redis Pub/Sub 구독자를 재시작")
    @PostMapping("/restart/subscriber")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> restartSubscriber() {
        try {
            boolean wasSubscribed = eventSubscriber.isSubscribed();

            // 구독자 재시작 시도
            boolean restartSuccess = eventSubscriber.restartSubscription();

            Map<String, Object> result = Map.of(
                    "wasSubscribed", wasSubscribed,
                    "restartSuccess", restartSuccess,
                    "currentStatus", eventSubscriber.getSubscriberStats(),
                    "restartTime", java.time.LocalDateTime.now()
            );

            if (restartSuccess) {
                log.info("관리자 Redis 구독자 재시작 성공: {}", result);
                return ResponseEntity.ok(SuccessResponse.of("구독자 재시작 성공", result));
            } else {
                log.error("관리자 Redis 구독자 재시작 실패: {}", result);
                return ResponseEntity.status(500)
                        .body(SuccessResponse.of("구독자 재시작 실패", result));
            }

        } catch (Exception e) {
            log.error("Redis 구독자 재시작 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("구독자 재시작 중 오류 발생", null));
        }
    }

    /**
     * 테스트 이벤트 발행
     */
    @Operation(summary = "테스트 이벤트 발행", description = "특정 콘서트에 테스트 이벤트를 발행")
    @PostMapping("/test/event")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> publishTestEvent(
            @Parameter(description = "콘서트 ID", example = "1")
            @RequestParam Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @RequestParam Long seatId) {

        try {
            // 테스트 이벤트 발행 전 상태
            int sessionsBefore = sessionManager.getSessionCount(concertId);

            // 테스트 이벤트 발행
            eventPublisher.publishTestEvent(concertId, seatId);

            Map<String, Object> result = Map.of(
                    "concertId", concertId,
                    "seatId", seatId,
                    "targetSessions", sessionsBefore,
                    "channelName", eventPublisher.getChannelName(concertId),
                    "publishTime", java.time.LocalDateTime.now()
            );

            log.info("관리자 테스트 이벤트 발행: {}", result);
            return ResponseEntity.ok(SuccessResponse.of("테스트 이벤트 발행 완료", result));

        } catch (Exception e) {
            log.error("테스트 이벤트 발행 중 오류: concertId={}, seatId={}", concertId, seatId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("테스트 이벤트 발행 실패", null));
        }
    }

    /**
     * 발행자 통계 초기화
     */
    @Operation(summary = "발행자 통계 초기화", description = "이벤트 발행자의 통계를 초기화")
    @PostMapping("/reset/publisher-stats")
    public ResponseEntity<SuccessResponse<String>> resetPublisherStats() {
        try {
            eventPublisher.resetStats();

            log.info("관리자 발행자 통계 초기화 실행");
            return ResponseEntity.ok(SuccessResponse.of("발행자 통계 초기화 완료", "통계가 성공적으로 초기화되었습니다"));

        } catch (Exception e) {
            log.error("발행자 통계 초기화 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("발행자 통계 초기화 실패", null));
        }
    }

    /**
     * 특정 사용자의 세션 정보 조회
     */
    @Operation(summary = "사용자별 세션 조회", description = "특정 사용자의 활성 세션 정보")
    @GetMapping("/users/{userId}/sessions")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getUserSessions(
            @Parameter(description = "사용자 ID", example = "100")
            @PathVariable Long userId) {

        try {
            Map<String, Object> userSessionInfo = Map.of(
                    "userId", userId,
                    "activeSessionCount", sessionManager.getUserSessionCount(userId),
                    "totalSystemSessions", sessionManager.getTotalSessionCount(),
                    "checkTime", java.time.LocalDateTime.now()
            );

            return ResponseEntity.ok(SuccessResponse.of("사용자 세션 정보 조회 성공", userSessionInfo));

        } catch (Exception e) {
            log.error("사용자 세션 정보 조회 중 오류: userId={}", userId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("사용자 세션 정보 조회 실패", null));
        }
    }

    /**
     * 시스템 헬스 상태 계산 (내부 메서드)
     */
    private Map<String, Object> calculateSystemHealth() {
        try {
            // 구독자 상태 확인
            boolean subscriberHealthy = eventSubscriber.isSubscribed();

            // 구독자 성공률 확인
            Map<String, Object> subscriberStats = eventSubscriber.getSubscriberStats();
            double subscriberSuccessRate = (Double) subscriberStats.get("successRate");

            // 발행자 성공률 확인
            Map<String, Object> publisherStats = eventPublisher.getPublisherStats();
            double publisherSuccessRate = (Double) publisherStats.get("successRate");

            // 전체 세션 수 확인
            int totalSessions = sessionManager.getTotalSessionCount();
            boolean sessionLoadHealthy = totalSessions < 8000; // 8000개 미만이면 건강

            // 전체 건강도 계산
            boolean overallHealthy = subscriberHealthy &&
                    subscriberSuccessRate >= 95.0 &&
                    publisherSuccessRate >= 95.0 &&
                    sessionLoadHealthy;

            return Map.of(
                    "overall", overallHealthy ? "HEALTHY" : "UNHEALTHY",
                    "subscriber", subscriberHealthy ? "HEALTHY" : "UNHEALTHY",
                    "subscriberSuccessRate", subscriberSuccessRate,
                    "publisherSuccessRate", publisherSuccessRate,
                    "sessionLoad", sessionLoadHealthy ? "NORMAL" : "HIGH",
                    "totalSessions", totalSessions,
                    "checkTime", java.time.LocalDateTime.now()
            );

        } catch (Exception e) {
            log.error("시스템 헬스 계산 중 오류", e);
            return Map.of(
                    "overall", "ERROR",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 콘서트 로드 퍼센티지 계산 (내부 메서드)
     */
    private double calculateLoadPercentage(Long concertId) {
        int currentSessions = sessionManager.getSessionCount(concertId);
        int maxSessions = 1000; // TODO: 설정에서 가져오기
        return (double) currentSessions / maxSessions * 100.0;
    }
}