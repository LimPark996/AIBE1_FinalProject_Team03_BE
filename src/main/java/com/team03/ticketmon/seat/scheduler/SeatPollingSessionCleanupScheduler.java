package com.team03.ticketmon.seat.scheduler;

import com.team03.ticketmon.seat.service.SeatPollingSessionManager;
import com.team03.ticketmon.seat.service.SeatStatusEventSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SeatPollingSessionCleanupScheduler — 폴링 세션 정리 스케줄러
 *
 * 이 클래스가 하는 일:
 *   1. 2분마다 {@link SeatPollingSessionManager}의 만료/완료 세션을 정리(메모리 누수 방지)
 *   2. 정리 전/후의 세션/콘서트 수를 비교해 의미있는 변경이 있을 때만 로깅
 *   3. {@link SeatStatusEventSubscriber}의 Redis 구독 상태를 주기 모니터링
 *   4. 관리자 대시보드에 노출할 스케줄러 상태 지표를 보관
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatPollingSessionCleanupScheduler {

    private final SeatPollingSessionManager sessionManager;
    private final SeatStatusEventSubscriber eventSubscriber;

    /**
     * 만료된 폴링 세션 정리 (2분마다 실행)
     */
    @Scheduled(fixedRate = 120000) // 2분 = 120,000ms
    public void cleanupExpiredSessions() {
        try {
            log.debug("만료된 폴링 세션 정리 시작");

            // 정리 전 상태 기록
            int sessionsBefore = sessionManager.getTotalSessionCount();
            int concertsBefore = sessionManager.getActiveConcertCount();

            // 실제 정리 수행
            sessionManager.cleanupExpiredSessions();

            // 정리 후 상태 기록
            int sessionsAfter = sessionManager.getTotalSessionCount();
            int concertsAfter = sessionManager.getActiveConcertCount();
            int cleanedSessions = sessionsBefore - sessionsAfter;
            int cleanedConcerts = concertsBefore - concertsAfter;

            // 정리 결과 로깅
            if (cleanedSessions > 0 || cleanedConcerts > 0) {
                log.info("폴링 세션 정리 완료: cleanedSessions={}, cleanedConcerts={}, " +
                                "remainingSessions={}, remainingConcerts={}",
                        cleanedSessions, cleanedConcerts, sessionsAfter, concertsAfter);
            }

        } catch (Exception e) {
            log.error("폴링 세션 정리 중 오류 발생", e);
        }
    }

    /**
     * 시스템 상태 모니터링 (5분마다 실행)
     */
    @Scheduled(fixedRate = 300000) // 5분 = 300,000ms
    public void monitorSystemStatus() {
        try {
            // 세션 매니저 상태 조회
            Map<String, Object> sessionStats = sessionManager.getSystemStatus();

            // 이벤트 구독자 상태 조회
            Map<String, Object> subscriberStats = eventSubscriber.getSubscriberStats();

            log.info("시스템 상태 모니터링 - 세션: {}, 구독자: {}", sessionStats, subscriberStats);

            // 구독자가 비활성화된 경우 경고
            if (!(Boolean) subscriberStats.get("isSubscribed")) {
                log.warn("⚠️ Redis 이벤트 구독자가 비활성화되어 있습니다. 재시작을 시도합니다.");

                // 구독 재시작 시도
                boolean restartSuccess = eventSubscriber.restartSubscription();
                if (restartSuccess) {
                    log.info("✅ Redis 이벤트 구독자 재시작 성공");
                } else {
                    log.error("❌ Redis 이벤트 구독자 재시작 실패");
                }
            }

            // 높은 세션 수에 대한 경고
            int totalSessions = (Integer) sessionStats.get("totalSessions");
            int activeConcerts = (Integer) sessionStats.get("activeConcerts");

            if (totalSessions > 5000) {
                log.warn("⚠️ 높은 세션 수 감지: totalSessions={}, activeConcerts={}",
                        totalSessions, activeConcerts);
            }

        } catch (Exception e) {
            log.error("시스템 상태 모니터링 중 오류 발생", e);
        }
    }

    /**
     * 상세 시스템 통계 리포트 (30분마다 실행)
     */
    @Scheduled(fixedRate = 1800000) // 30분 = 1,800,000ms  
    public void generateSystemReport() {
        try {
            log.info("=== 좌석 폴링 시스템 상세 리포트 ===");

            // 세션 매니저 통계
            Map<String, Object> sessionStats = sessionManager.getSystemStatus();
            log.info("📊 세션 통계: {}", sessionStats);

            // 이벤트 구독자 통계
            Map<String, Object> subscriberStats = eventSubscriber.getSubscriberStats();
            log.info("📡 구독자 통계: {}", subscriberStats);

            // 성능 지표 계산
            double subscriberSuccessRate = (Double) subscriberStats.get("successRate");
            long processedEvents = (Long) subscriberStats.get("processedEventCount");
            long errorEvents = (Long) subscriberStats.get("errorEventCount");

            log.info("📈 성능 지표: 구독자성공률={}%, 처리이벤트={}, 오류이벤트={}",
                    String.format("%.2f", subscriberSuccessRate), processedEvents, errorEvents);

            // 메모리 사용량 정보
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();

            log.info("💾 메모리 사용량: 사용중={}MB, 전체={}MB, 최대={}MB, 사용률={}%",
                    usedMemory / 1024 / 1024,
                    totalMemory / 1024 / 1024,
                    maxMemory / 1024 / 1024,
                    String.format("%.2f", (double) usedMemory / maxMemory * 100));

            log.info("=== 리포트 완료 ===");

        } catch (Exception e) {
            log.error("시스템 리포트 생성 중 오류 발생", e);
        }
    }

    /**
     * 긴급 상황 감지 및 대응 (1분마다 실행)
     */
    @Scheduled(fixedRate = 60000) // 1분 = 60,000ms
    public void emergencyMonitoring() {
        try {
            int totalSessions = sessionManager.getTotalSessionCount();

            // 세션 수가 10,000개를 초과하는 경우 긴급 정리
            if (totalSessions > 10000) {
                log.warn("🚨 긴급 상황: 과도한 세션 수 감지 ({}개). 강제 정리를 수행합니다.", totalSessions);

                // 강제 정리 수행
                sessionManager.cleanupExpiredSessions();

                int sessionsAfter = sessionManager.getTotalSessionCount();
                log.warn("🚨 긴급 정리 완료: {} -> {} ({}개 정리)",
                        totalSessions, sessionsAfter, totalSessions - sessionsAfter);
            }

            // Redis 구독자 상태 긴급 체크
            if (!eventSubscriber.isSubscribed()) {
                log.error("🚨 긴급 상황: Redis 구독자가 비활성화됨. 즉시 재시작 시도.");
                eventSubscriber.restartSubscription();
            }

        } catch (Exception e) {
            log.error("긴급 모니터링 중 오류 발생", e);
        }
    }

    /**
     * 수동 세션 정리 트리거 (외부에서 호출 가능)
     */
    public void triggerManualCleanup() {
        log.info("수동 세션 정리 트리거됨");
        try {
            cleanupExpiredSessions();
            log.info("수동 세션 정리 완료");
        } catch (Exception e) {
            log.error("수동 세션 정리 실패", e);
            throw new RuntimeException("수동 세션 정리 실패", e);
        }
    }

    /**
     * 스케줄러 상태 확인
     */
    public Map<String, Object> getSchedulerStatus() {
        return Map.of(
                "cleanupSchedulerActive", true,
                "monitoringSchedulerActive", true,
                "reportSchedulerActive", true,
                "emergencyMonitoringActive", true,
                "schedulerInfo", Map.of(
                        "cleanupInterval", "2분",
                        "monitoringInterval", "5분",
                        "reportInterval", "30분",
                        "emergencyInterval", "1분"
                )
        );
    }
}