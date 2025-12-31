package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.seat.config.SeatProperties;
import com.team03.ticketmon.seat.service.SeatPollingSessionManager;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 좌석 상태 실시간 폴링 컨트롤러 (개선된 버전)
 * - Long Polling 방식으로 좌석 상태 변경사항을 실시간으로 클라이언트에 전달
 * - DeferredResult를 활용한 비동기 응답 처리
 * - 성능 최적화 및 오류 처리 강화
 */
@Tag(name = "좌석 실시간 폴링", description = "좌석 상태 실시간 업데이트 API (Long Polling)")
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatPollingController {

    private final SeatPollingSessionManager sessionManager;
    private final SeatStatusService seatStatusService;
    private final SeatProperties seatProperties;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 좌석 상태 실시간 폴링 API (개선된 버전)
     * - 좌석 상태 변경 시 즉시 응답, 변경사항 없으면 타임아웃까지 대기
     * - 클라이언트 정보 수집 및 오류 처리 강화
     *
     * @param concertId 콘서트 ID
     * @param lastUpdateTime 클라이언트가 마지막으로 받은 업데이트 시간 (선택적)
     * @param timeout 폴링 타임아웃 (ms, 기본 30초)
     * @param request HTTP 요청 (User-Agent 등 추출용)
     * @return DeferredResult로 비동기 응답
     * user.getUserId() JWT 토큰에서 추출한 사용자 ID
     */
    @Operation(summary = "좌석 상태 실시간 폴링",
            description = "좌석 상태 변경 시 즉시 응답, 변경사항 없으면 최대 30초 대기")
    @GetMapping("/concerts/{concertId}/polling")
    public DeferredResult<ResponseEntity<?>> pollSeatUpdates(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,

            @Parameter(description = "마지막 업데이트 시간 (ISO 형식)", example = "2025-06-27T10:30:00")
            @RequestParam(required = false) String lastUpdateTime,

            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String section,

            @Parameter(description = "폴링 타임아웃 (밀리초)", example = "30000")
            @RequestParam(defaultValue = "30000") long timeout,

            @Parameter(description = "기존 세션 교체 여부", example = "false")
            @RequestParam(defaultValue = "false") boolean replace,

            HttpServletRequest request) {

        // ✅ JWT 토큰 검증 (수동 인증 처리)
        String accessToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_ACCESS, request);
        final Long userId;
        
        if (accessToken == null || jwtTokenProvider.isTokenExpired(accessToken)) {
            Map<String, Object> authErrorResponse = Map.of(
                    "hasUpdate", false,
                    "message", "인증이 필요합니다. 로그인해주세요.",
                    "errorCode", "AUTHENTICATION_REQUIRED"
            );
            DeferredResult<ResponseEntity<?>> authErrorResult = new DeferredResult<>();
            authErrorResult.setResult(ResponseEntity.status(401)
                    .body(SuccessResponse.of("인증 실패", authErrorResponse)));
            return authErrorResult;
        }
        
        try {
            Long extractedUserId = jwtTokenProvider.getUserId(accessToken);
            if (extractedUserId == null) {
                throw new RuntimeException("토큰에서 사용자 ID를 추출할 수 없습니다.");
            }
            userId = extractedUserId;
        } catch (Exception e) {
            log.warn("JWT 토큰 파싱 실패: {}", e.getMessage());
            Map<String, Object> tokenErrorResponse = Map.of(
                    "hasUpdate", false,
                    "message", "유효하지 않은 토큰입니다.",
                    "errorCode", "INVALID_TOKEN"
            );
            DeferredResult<ResponseEntity<?>> tokenErrorResult = new DeferredResult<>();
            tokenErrorResult.setResult(ResponseEntity.status(401)
                    .body(SuccessResponse.of("토큰 오류", tokenErrorResponse)));
            return tokenErrorResult;
        }

        // ✅ 개선: 입력 검증 강화
        if (concertId == null || concertId <= 0) {
            Map<String, Object> errorResponse = Map.of(
                    "hasUpdate", false,
                    "message", "유효하지 않은 콘서트 ID입니다."
            );
            DeferredResult<ResponseEntity<?>> errorResult = new DeferredResult<>();
            errorResult.setResult(ResponseEntity.badRequest()
                    .body(SuccessResponse.of("잘못된 요청", errorResponse)));
            return errorResult;
        }

        // ✅ 수정: 타임아웃 범위 검증 (final 변수로 생성)
        final long finalTimeout = Math.min(Math.max(timeout, seatProperties.getPolling().getMinTimeoutMs()), 
                                          seatProperties.getPolling().getMaxTimeoutMs());

        // ✅ 개선: 클라이언트 정보 수집
        String userAgent = request.getHeader("User-Agent");
        String clientIp = getClientIpAddress(request);

        // DeferredResult 생성 (final 변수 사용)
        DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>(finalTimeout);

        try {
            // 세션 수 제한 확인
            int maxSessions = seatProperties.getSession().getMaxSessionsPerConcert();
            if (sessionManager.getSessionCount(concertId) >= maxSessions) {
                Map<String, Object> overloadResponse = Map.of(
                        "hasUpdate", false,
                        "message", "서버 과부하로 인해 즉시 응답합니다. 잠시 후 다시 시도해주세요.",
                        "recommendedRetryAfter", 5000,
                        "currentLoad", sessionManager.getSessionCount(concertId),
                        "maxCapacity", maxSessions
                );
                deferredResult.setResult(ResponseEntity.status(503) // Service Unavailable
                        .body(SuccessResponse.of("서버 과부하", overloadResponse)));
                return deferredResult;
            }

            // ✅ 개선: 최근 업데이트 시간 파싱 및 즉시 응답 여부 확인
            LocalDateTime lastUpdate = parseLastUpdateTime(lastUpdateTime);
            if (lastUpdate != null && hasRecentUpdates(concertId, lastUpdate)) {
                // 최근 변경사항이 있으면 즉시 현재 상태 응답
                Map<String, Object> immediateResponse = getCurrentSeatStatusResponse(concertId, grade, section);
                deferredResult.setResult(ResponseEntity.ok(SuccessResponse.of("즉시 응답", immediateResponse)));

                log.debug("즉시 응답 제공: concertId={}, userId={}, lastUpdate={}",
                        concertId, userId, lastUpdate);
                return deferredResult;
            }

            // ✅ 개선: 세션 등록 (replace 파라미터 고려)
            String sessionId;
            if (replace) {
                // 기존 세션 교체 모드
                sessionId = sessionManager.replaceUserSession(concertId, deferredResult, userId, userAgent);
            } else {
                // 일반 세션 등록 모드
                sessionId = sessionManager.registerSession(concertId, deferredResult, userId, userAgent);
            }

            if (sessionId == null) {
                // 세션 등록 실패 (서버 과부하)
                Map<String, Object> failResponse = Map.of(
                        "hasUpdate", false,
                        "message", "세션 등록 실패. 잠시 후 다시 시도해주세요.",
                        "errorCode", "SESSION_REGISTRATION_FAILED"
                );
                deferredResult.setResult(ResponseEntity.status(503)
                        .body(SuccessResponse.of("서비스 일시 불가", failResponse)));
                return deferredResult;
            }
            // USER_SESSION_EXISTS 체크 제거 (다중 세션 허용으로 더 이상 불필요)

            // ✅ 수정: 타임아웃 핸들러 설정 (final 변수 사용)
            deferredResult.onTimeout(() -> {
                Map<String, Object> timeoutResponse = Map.of(
                        "hasUpdate", false,
                        "message", "폴링 타임아웃",
                        "nextPollRecommended", true,
                        "timeoutMs", finalTimeout,
                        "serverTime", LocalDateTime.now()
                );
                deferredResult.setResult(ResponseEntity.ok(SuccessResponse.of("타임아웃", timeoutResponse)));

                log.debug("폴링 타임아웃: concertId={}, sessionId={}, userId={}, timeout={}ms",
                        concertId, sessionId, userId, finalTimeout);
            });

            // ✅ 개선: 에러 핸들러 설정
            deferredResult.onError(throwable -> {
                log.error("폴링 세션 에러: concertId={}, sessionId={}, userId={}, clientIp={}",
                        concertId, sessionId, userId, clientIp, throwable);

                Map<String, Object> errorResponse = Map.of(
                        "hasUpdate", false,
                        "message", "폴링 중 오류 발생",
                        "errorCode", "POLLING_ERROR"
                );
                deferredResult.setResult(ResponseEntity.status(500)
                        .body(SuccessResponse.of("서버 오류", errorResponse)));
            });

            // ✅ 개선: 완료 핸들러 설정 (로깅)
            deferredResult.onCompletion(() -> {
                log.debug("폴링 세션 완료: concertId={}, sessionId={}, userId={}",
                        concertId, sessionId, userId);
            });

            log.debug("폴링 세션 시작: concertId={}, sessionId={}, userId={}, timeout={}ms, clientIp={}",
                    concertId, sessionId, userId, finalTimeout, clientIp);

        } catch (Exception e) {
            log.error("폴링 요청 처리 중 오류: concertId={}, userId={}, clientIp={}",
                    concertId, userId, clientIp, e);

            Map<String, Object> errorResponse = Map.of(
                    "hasUpdate", false,
                    "message", "폴링 요청 처리 실패",
                    "errorCode", "REQUEST_PROCESSING_ERROR"
            );
            deferredResult.setResult(ResponseEntity.status(500)
                    .body(SuccessResponse.of("요청 처리 실패", errorResponse)));
        }

        return deferredResult;
    }

    /**
     * 폴링 시스템 상태 조회 (디버깅/모니터링용) - 개선된 버전
     */
    @Operation(summary = "폴링 시스템 상태 조회",
            description = "현재 활성 폴링 세션 정보 및 시스템 상태 조회")
    @GetMapping("/concerts/{concertId}/polling/status")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getPollingStatus(
            @PathVariable Long concertId) {

        try {
            Map<String, Object> status = Map.of(
                    "concertId", concertId,
                    "activeSessionCount", sessionManager.getSessionCount(concertId),
                    "totalActiveSessionCount", sessionManager.getTotalSessionCount(),
                    "activeConcertCount", sessionManager.getActiveConcertCount(),
                    "maxSessionsPerConcert", seatProperties.getSession().getMaxSessionsPerConcert(),
                    "timeoutSettings", Map.of(
                            "default", seatProperties.getPolling().getDefaultTimeoutMs(),
                            "min", seatProperties.getPolling().getMinTimeoutMs(),
                            "max", seatProperties.getPolling().getMaxTimeoutMs()
                    ),
                    "serverTime", LocalDateTime.now(),
                    "systemStatus", sessionManager.getSystemStatus()
            );

            return ResponseEntity.ok(SuccessResponse.of("폴링 상태 조회 성공", status));

        } catch (Exception e) {
            log.error("폴링 상태 조회 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("폴링 상태 조회 실패", null));
        }
    }

    /**
     * 전체 시스템 통계 조회 (관리자용)
     */
    @Operation(summary = "폴링 시스템 전체 통계", description = "전체 시스템의 폴링 세션 통계 조회")
    @GetMapping("/polling/stats")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getPollingStats() {
        try {
            Map<String, Object> stats = Map.of(
                    "totalActiveSessions", sessionManager.getTotalSessionCount(),
                    "activeConcerts", sessionManager.getActiveConcertCount(),
                    "systemStatus", sessionManager.getSystemStatus(),
                    "serverInfo", Map.of(
                            "serverTime", LocalDateTime.now(),
                            "maxSessionsPerConcert", seatProperties.getSession().getMaxSessionsPerConcert(),
                            "timeoutRange", seatProperties.getPolling().getMinTimeoutMs() + "ms - " + seatProperties.getPolling().getMaxTimeoutMs() + "ms"
                    )
            );

            return ResponseEntity.ok(SuccessResponse.of("시스템 통계 조회 성공", stats));

        } catch (Exception e) {
            log.error("폴링 시스템 통계 조회 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("시스템 통계 조회 실패", null));
        }
    }

    /**
     * 마지막 업데이트 시간 파싱 (개선된 버전)
     */
    private LocalDateTime parseLastUpdateTime(String lastUpdateTime) {
        if (lastUpdateTime == null || lastUpdateTime.trim().isEmpty()) {
            return null;
        }

        try {
            // ISO Local DateTime 형식 파싱
            return LocalDateTime.parse(lastUpdateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("잘못된 시간 형식: {}, 오류: {}", lastUpdateTime, e.getMessage());
            return null;
        }
    }

    /**
     * 최근 업데이트 여부 확인 (Redis 기반 구현)
     */
    private boolean hasRecentUpdates(Long concertId, LocalDateTime lastUpdate) {
        try {
            // SeatStatusService를 통해 최근 업데이트 시간 조회
            LocalDateTime lastUpdateTime = seatStatusService.getLastUpdateTime(concertId);
            
            // 마지막 업데이트 시간이 없거나 클라이언트 시간보다 최신이면 업데이트 있음
            if (lastUpdateTime == null) {
                return false;
            }
            
            return lastUpdateTime.isAfter(lastUpdate);
        } catch (Exception e) {
            log.warn("최근 업데이트 확인 중 오류: concertId={}, lastUpdate={}", concertId, lastUpdate, e);
            return false;
        }
    }

    /**
     * 현재 좌석 상태 응답 생성 (SeatStatusService 연동)
     */
    private Map<String, Object> getCurrentSeatStatusResponse(Long concertId, String grade, String section) {
        try {
            Map<String, Object> seatStatus = seatStatusService.getCurrentSeatStatus(concertId, grade, section);
            LocalDateTime lastUpdateTime = seatStatusService.getLastUpdateTime(concertId);

            return Map.of(
                    "hasUpdate", true,
                    "updateTime", lastUpdateTime != null ? lastUpdateTime : LocalDateTime.now(),
                    "seatUpdates", seatStatus,
                    "message", "현재 상태 조회",
                    "concertId", concertId
            );
        } catch (Exception e) {
            log.warn("현재 좌석 상태 조회 중 오류: concertId={}", concertId, e);
            return Map.of(
                    "hasUpdate", false,
                    "updateTime", LocalDateTime.now(),
                    "seatUpdates", Map.of(),
                    "message", "상태 조회 실패",
                    "concertId", concertId
            );
        }
    }

    /**
     * 클라이언트 IP 주소 추출 (프록시 고려)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}