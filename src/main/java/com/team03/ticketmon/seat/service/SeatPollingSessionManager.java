package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.seat.config.SeatProperties;
import com.team03.ticketmon.seat.dto.SeatUpdateEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SeatPollingSessionManager — Long Polling 세션 관리 컴포넌트
 *
 * 이 클래스가 하는 일:
 *   1. 콘서트별 활성 Long Polling 세션(DeferredResult)을 메모리에 보관
 *   2. 좌석 변경 이벤트 발생 시 해당 콘서트의 모든 대기 세션에 응답 전달
 *   3. 만료된/완료된 세션을 주기적으로 정리 (메모리 누수 방지)
 *   4. 세션 식별자 발급 및 사용자/UA 메타 정보 기록(디버깅 용도)
 *
 * 동시성 주의: activeSessions는 ConcurrentHashMap이며, 각 List 조작 시 동기화 필요.
 * 세션 정리는 {@link com.team03.ticketmon.seat.scheduler.SeatPollingSessionCleanupScheduler}
 * 에서 주기적으로 호출된다.
 */
@Slf4j
@Component
public class SeatPollingSessionManager {

    private final SeatProperties seatProperties;

    // 콘서트별 활성 세션 관리: concertId -> List<PollingSession>
    private final Map<Long, List<PollingSession>> activeSessions = new ConcurrentHashMap<>();

    // 세션 ID 생성기
    private final AtomicLong sessionIdGenerator = new AtomicLong(0);

    public SeatPollingSessionManager(SeatProperties seatProperties) {
        this.seatProperties = seatProperties;
    }

    /**
     * 폴링 세션 정보를 담는 내부 클래스 (개선된 버전)
     */
    public static class PollingSession {
        private final String sessionId;
        private final DeferredResult<ResponseEntity<?>> deferredResult;
        private final LocalDateTime startTime;
        private final Long userId; // 선택적
        private final String userAgent; // 디버깅용 추가

        public PollingSession(String sessionId, DeferredResult<ResponseEntity<?>> deferredResult,
                              Long userId, String userAgent) {
            this.sessionId = sessionId;
            this.deferredResult = deferredResult;
            this.startTime = LocalDateTime.now();
            this.userId = userId;
            this.userAgent = userAgent; // 새로 추가
        }

        // Getters (✅ typo 수정)
        public String getSessionId() { return sessionId; }
        public DeferredResult<ResponseEntity<?>> getDeferredResult() { return deferredResult; }
        public LocalDateTime getStartTime() { return startTime; }
        public Long getUserId() { return userId; }
        public String getUserAgent() { return userAgent; } // 새로 추가
    }

    /**
     * 새로운 폴링 세션 등록 (개선된 버전)
     *
     * @param concertId 콘서트 ID
     * @param deferredResult DeferredResult 객체
     * @param userId 사용자 ID (선택적)
     * @param userAgent 사용자 에이전트 (디버깅용, 선택적)
     * @return 등록된 세션 ID (null이면 등록 실패)
     */
    public String registerSession(Long concertId, DeferredResult<ResponseEntity<?>> deferredResult,
                                  Long userId, String userAgent) {

        // 입력 검증 강화
        if (concertId == null || deferredResult == null) {
            log.warn("필수 파라미터가 null입니다: concertId={}, deferredResult={}", concertId, deferredResult);
            return null;
        }

        // 사용자별 활성 세션 제한 해제 (다중 세션 허용)

        // 세션 수 제한 확인
        int maxSessions = seatProperties.getSession().getMaxSessionsPerConcert();
        if (getSessionCount(concertId) >= maxSessions) {
            log.warn("콘서트 최대 세션 수 초과: concertId={}, currentCount={}",
                    concertId, getSessionCount(concertId));
            return null;
        }

        String sessionId = generateSessionId();
        PollingSession session = new PollingSession(sessionId, deferredResult, userId, userAgent);

        // 콘서트별 세션 리스트에 추가 (스레드 안전성 보장)
        activeSessions.computeIfAbsent(concertId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(session);

        // DeferredResult 완료/타임아웃 시 자동 정리
        deferredResult.onCompletion(() -> removeSession(concertId, sessionId));
        deferredResult.onTimeout(() -> removeSession(concertId, sessionId));

        log.debug("폴링 세션 등록: concertId={}, sessionId={}, userId={}, totalSessions={}",
                concertId, sessionId, userId, getSessionCount(concertId));

        return sessionId;
    }

    /**
     * 특정 콘서트의 모든 대기 세션에 이벤트 알림 (개선된 버전)
     *
     * @param event 좌석 업데이트 이벤트
     */
    public void notifyWaitingSessions(SeatUpdateEventDTO event) {
        Long concertId = event.concertId();
        List<PollingSession> sessions = activeSessions.get(concertId);

        if (sessions == null || sessions.isEmpty()) {
            log.debug("알림할 세션이 없음: concertId={}", concertId);
            return;
        }

        // 동시 수정 방지를 위해 복사본에서 작업
        List<PollingSession> sessionsCopy = new ArrayList<>(sessions);
        int notifiedCount = 0;
        int errorCount = 0;

        for (PollingSession session : sessionsCopy) {
            try {
                DeferredResult<ResponseEntity<?>> deferredResult = session.getDeferredResult();

                if (!deferredResult.isSetOrExpired()) {
                    // 응답 데이터 구성
                    Map<String, Object> response = createEventResponse(event);
                    deferredResult.setResult(ResponseEntity.ok(response));
                    notifiedCount++;

                    log.debug("세션 알림 성공: concertId={}, sessionId={}, userId={}",
                            concertId, session.getSessionId(), session.getUserId());
                } else {
                    log.debug("만료된 세션 스킵: concertId={}, sessionId={}",
                            concertId, session.getSessionId());
                }
            } catch (Exception e) {
                errorCount++;
                log.warn("세션 알림 실패: concertId={}, sessionId={}, error={}",
                        concertId, session.getSessionId(), e.getMessage());
            }
        }

        log.info("좌석 업데이트 알림 완료: concertId={}, seatId={}, notified={}/{}, errors={}",
                concertId, event.seatId(), notifiedCount, sessionsCopy.size(), errorCount);
    }

    /**
     * 세션 제거 (개선된 버전)
     *
     * @param concertId 콘서트 ID
     * @param sessionId 세션 ID
     */
    public void removeSession(Long concertId, String sessionId) {
        List<PollingSession> sessions = activeSessions.get(concertId);
        if (sessions != null) {
            // 세션 제거 (스레드 안전)
            sessions.removeIf(session -> sessionId.equals(session.getSessionId()));

            // 빈 리스트이면 맵에서 제거 (메모리 절약)
            if (sessions.isEmpty()) {
                activeSessions.remove(concertId);
            }

            log.debug("폴링 세션 제거: concertId={}, sessionId={}, remainingSessions={}",
                    concertId, sessionId, sessions.size());
        }
    }

    /**
     * 만료된 세션들 정리 (스케줄러에서 호출) - 개선된 버전
     */
    public void cleanupExpiredSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(seatProperties.getSession().getCleanupMinutes());
        int cleanedCount = 0;
        int totalSessionsBefore = getTotalSessionCount();

        // ConcurrentHashMap이므로 iterator 사용 가능
        Iterator<Map.Entry<Long, List<PollingSession>>> concertIterator = activeSessions.entrySet().iterator();

        while (concertIterator.hasNext()) {
            Map.Entry<Long, List<PollingSession>> entry = concertIterator.next();
            Long concertId = entry.getKey();
            List<PollingSession> sessions = entry.getValue();

            // 각 콘서트별 세션 정리
            Iterator<PollingSession> sessionIterator = sessions.iterator();
            while (sessionIterator.hasNext()) {
                PollingSession session = sessionIterator.next();

                // 만료 조건: 설정 시간 이상 된 세션 또는 이미 처리된 세션
                if (session.getStartTime().isBefore(cutoffTime) ||
                        session.getDeferredResult().isSetOrExpired()) {
                    sessionIterator.remove();
                    cleanedCount++;

                    log.debug("만료된 세션 정리: concertId={}, sessionId={}, startTime={}",
                            concertId, session.getSessionId(), session.getStartTime());
                }
            }

            // 빈 세션 리스트 제거
            if (sessions.isEmpty()) {
                concertIterator.remove();
            }
        }

        if (cleanedCount > 0) {
            log.info("만료된 폴링 세션 정리 완료: cleaned={}, before={}, after={}, activeConcerts={}",
                    cleanedCount, totalSessionsBefore, getTotalSessionCount(), activeSessions.size());
        }
    }

    /**
     * 특정 콘서트의 활성 세션 수 조회
     */
    public int getSessionCount(Long concertId) {
        List<PollingSession> sessions = activeSessions.get(concertId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * 전체 활성 세션 수 조회
     */
    public int getTotalSessionCount() {
        return activeSessions.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 전체 활성 콘서트 수 조회 (모니터링용)
     */
    public int getActiveConcertCount() {
        return activeSessions.size();
    }

    /**
     * 특정 사용자의 세션 수 조회 (디버깅용)
     */
    public long getUserSessionCount(Long userId) {
        if (userId == null) return 0;

        return activeSessions.values().stream()
                .flatMap(List::stream)
                .filter(session -> userId.equals(session.getUserId()))
                .count();
    }

    /**
     * 특정 사용자가 특정 콘서트에서 활성 세션을 가지고 있는지 확인
     */
    public boolean hasActiveUserSession(Long userId, Long concertId) {
        if (userId == null || concertId == null) return false;

        List<PollingSession> sessions = activeSessions.get(concertId);
        if (sessions == null || sessions.isEmpty()) return false;

        return sessions.stream()
                .anyMatch(session -> userId.equals(session.getUserId()) && 
                         !session.getDeferredResult().isSetOrExpired());
    }

    /**
     * 특정 사용자의 기존 세션을 종료하고 새 세션을 등록
     */
    public String replaceUserSession(Long concertId, DeferredResult<ResponseEntity<?>> deferredResult,
                                   Long userId, String userAgent) {
        if (userId == null) {
            return registerSession(concertId, deferredResult, userId, userAgent);
        }

        // 기존 세션 종료
        terminateUserSession(userId, concertId);

        // 새 세션 등록
        return registerSession(concertId, deferredResult, userId, userAgent);
    }

    /**
     * 특정 사용자의 활성 세션 종료
     */
    public void terminateUserSession(Long userId, Long concertId) {
        if (userId == null || concertId == null) return;

        List<PollingSession> sessions = activeSessions.get(concertId);
        if (sessions == null || sessions.isEmpty()) return;

        sessions.removeIf(session -> {
            if (userId.equals(session.getUserId())) {
                DeferredResult<ResponseEntity<?>> deferredResult = session.getDeferredResult();
                if (!deferredResult.isSetOrExpired()) {
                    // 세션 종료 응답
                    Map<String, Object> response = Map.of(
                            "hasUpdate", false,
                            "message", "새로운 폴링 세션으로 교체됨",
                            "sessionTerminated", true
                    );
                    deferredResult.setResult(ResponseEntity.ok(response));
                }
                log.debug("사용자 세션 종료: userId={}, concertId={}, sessionId={}", 
                         userId, concertId, session.getSessionId());
                return true;
            }
            return false;
        });

        // 빈 리스트이면 맵에서 제거
        if (sessions.isEmpty()) {
            activeSessions.remove(concertId);
        }
    }

    /**
     * 이벤트 응답 데이터 구성 (개선된 버전)
     */
    private Map<String, Object> createEventResponse(SeatUpdateEventDTO event) {
        Map<String, Object> response = new HashMap<>();
        response.put("hasUpdate", true);
        response.put("updateTime", event.timestamp());
        response.put("eventType", "SEAT_STATUS_CHANGE");
        
        Map<String, Object> seatUpdate = new HashMap<>();
        seatUpdate.put("seatId", event.seatId());
        seatUpdate.put("status", event.status().toString());
        seatUpdate.put("userId", event.userId()); // null 값 허용
        seatUpdate.put("seatInfo", event.seatInfo());
        
        response.put("seatUpdates", List.of(seatUpdate));
        response.put("serverTime", LocalDateTime.now());
        
        return response;
    }

    /**
     * 고유 세션 ID 생성 (개선된 버전)
     */
    private String generateSessionId() {
        return "session-" + sessionIdGenerator.incrementAndGet() + "-" + System.currentTimeMillis();
    }

    /**
     * 시스템 상태 조회 (모니터링용)
     */
    public Map<String, Object> getSystemStatus() {
        return Map.of(
                "totalSessions", getTotalSessionCount(),
                "activeConcerts", getActiveConcertCount(),
                "maxSessionsPerConcert", seatProperties.getSession().getMaxSessionsPerConcert(),
                "sessionCleanupMinutes", seatProperties.getSession().getCleanupMinutes(),
                "lastCleanupTime", LocalDateTime.now()
        );
    }
}