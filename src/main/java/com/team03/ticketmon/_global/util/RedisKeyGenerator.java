package com.team03.ticketmon._global.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * ✅ RedisKeyGenerator: Redis 키 생성을 중앙에서 관리하는 유틸 클래스<br>
 * -----------------------------------------------------------<br>
 * 대기열, 세션, 접근키 등 Redis 기반의 상태 데이터를 사용할 때<br>
 * 일관된 키 네이밍 전략을 통해 오류를 방지하고 유지보수를 용이하게 합니다.<br><br>
 *
 * 📌 키 구성 규칙:<br>
 * 접두사(prefix)를 통해 도메인 영역을 구분하고, 하위 리소스를 `:`로 연결합니다.<br>
 *
 * <ul>
 *     <li>waitqueue:concert:{concertId}         → 콘서트별 대기열 Sorted Set</li>
 *     <li>active_sessions:concert:{concertId}   → 콘서트별 활성 세션 Sorted Set</li>
 *     <li>active_users_count:concert:{concertId}→ 콘서트별 활성 사용자 수 AtomicLong</li>
 *     <li>accesskey:concert:{concertId}:user:{userId} → 사용자별 입장 AccessKey</li>
 * </ul>
 *
 * <br>
 * 📌 사용 이유:
 * <ul>
 *     <li>중앙 관리로 키 일관성 유지</li>
 *     <li>하드코딩 방지 및 오타 예방</li>
 *     <li>도메인별 Prefix 구조로 Redis 조회/삭제 용이</li>
 * </ul>
 */
@Component
public class RedisKeyGenerator {

    private static final String CONCERT_PREFIX = "concert:";
    private static final String USER_PREFIX = "user:";
    public static final String JWT_RT_PREFIX = "refreshToken:";

    /** 🔑 `waitqueue:concert:{concertId}`<br>
     * 콘서트별 대기열 정보를 담는 Sorted Set 키입니다.<br>
     * - score: 대기 순번용 timestamp + sequence<br>
     * - value: userId
     */
    private static final String WAIT_QUEUE_KEY_PREFIX = "waitqueue:";

    /** 🔑 `active_sessions:concert:{concertId}`<br>
     * 콘서트별 활성 사용자의 세션 정보를 저장하는 Sorted Set 키입니다.<br>
     * - score: 만료 시간 (timestamp)<br>
     * - value: userId
     */
    private static final String ACTIVE_SESSIONS_KEY_PREFIX = "active_sessions:";

    /** 🔑 `active_users_count:concert:{concertId}`<br>
     * 콘서트별 현재 활성 사용자 수를 저장하는 AtomicLong 키입니다.
     */
    private static final String ACTIVE_USERS_COUNT_KEY_PREFIX = "active_users_count:";

    /** 🔑 `accesskey:concert:{concertId}:user:{userId}`<br>
     * 입장 허가(AccessKey)를 저장하는 사용자별 String(Bucket) 키입니다.<br>
     * TTL 기반으로 자동 만료되며, 입장 검증 필터에서 사용됩니다.
     */
    private static final String ACCESS_KEY_PREFIX = "accesskey:";

    /** * 🔑 `final_expiry:concert:{concertId}:user:{userId}`<br>
     * 세션의 절대적인 최종 만료 시각(timestamp)을 저장하는 키입니다.<br>
     * 이 키의 값은 연장되지 않으며, 세션의 최대 수명을 제한하는 기준으로 사용됩니다.
     */
    private static final String FINAL_EXPIRY_KEY_PREFIX = "final_expiry:";

    // --- 스케줄러 락 키 ---

    /** 🔒 `lock:cleanupScheduler`<br>
     * 만료 세션 정리 작업용 분산 락 키입니다.
     */
//    public static final String CLEANUP_SCHEDULER_LOCK_KEY = "lock:cleanupScheduler";
    public static final String CLEANUP_SCHEDULER_LOCK_KEY = "lock:queueScheduler";

    /** 🔒 `lock:admissionScheduler`<br>
     * 대기열 입장 처리용 스케줄러 락 키입니다.
     */
//    public static final String ADMISSION_SCHEDULER_LOCK_KEY = "lock:admissionScheduler";
    public static final String ADMISSION_SCHEDULER_LOCK_KEY = "lock:queueScheduler";

    /** 🔒 `lock:consistencyCheckScheduler`<br>
     * 정합성 체크 스케줄러 락 키입니다.
     */
//    public static final String CONSISTENCY_CHECK_LOCK_KEY = "lock:consistencyCheckScheduler";
    public static final String CONSISTENCY_CHECK_LOCK_KEY = "lock:queueScheduler";

    // --- 🪑 좌석 관리 관련 키 ---

    public static final String SEAT_STATUS_KEY_PREFIX = "seat:status:";

    public static final String SEAT_LOCK_KEY_PREFIX = "seat:lock:";

    public static final String SEAT_TTL_KEY_PREFIX = "seat:expire:";

    public static final String SEAT_CHANNEL_PATTERN = "seat:status:update:*";

    public static final String SEAT_CHANNEL_PREFIX = "seat:status:update:";

    public static final String SEAT_LAST_UPDATE_KEY_PREFIX = "seat:last_update:";

    public static final String USER_RESERVED_KEY_PREFIX = "user:reserved:";

    // --- 🪑 Warm-up ---

    public static final String WARMUP_LOCK_KEY = "lock:seat:cache:warmup";

    public static final String SEAT_PROCESSED_CONCERT_KEY_PREFIX = "processed:warmup:concert:";

    // --- 🪑 keyspace-event ---

    public static final String SEAT_EXPIRE_KEY_PATTERN = "seat:expire:*";
    public static final Pattern SEAT_KEY_REGEX = Pattern.compile("seat:expire:(\\d+):(\\d+)");

    // --- Pub/Sub 토픽 관련 키 ---

    /**
     * 📣 `admission-channel`<br>
     * 입장 허가 이벤트를 전달하는 Redis Pub/Sub 채널 이름입니다.<br>
     * WebSocket 서버가 이 채널을 구독하여 실시간 알림을 전송합니다.
     */
    public static final String ADMISSION_TOPIC = "admission-channel";

    /**
     * 📣 `rank-update-channel`<br>
     * 순위 업데이트 이벤트를 전달하는 Redis Pub/Sub 채널 이름입니다.<br>
     */
    public static final String RANK_UPDATE_TOPIC = "rank-update-channel";

    /**
     * 🎯 콘서트별 대기열 키 생성
     * @param concertId 콘서트 ID
     * @return Redis 키: `waitqueue:concert:{concertId}`
     */
    public String getWaitQueueKey(Long concertId) {
        return WAIT_QUEUE_KEY_PREFIX + CONCERT_PREFIX + concertId;
    }

    /**
     * 🎯 콘서트별 활성 세션 키 생성
     * @param concertId 콘서트 ID
     * @return Redis 키: `active_sessions:concert:{concertId}`
     */
    public String getActiveSessionsKey(Long concertId) {
        return ACTIVE_SESSIONS_KEY_PREFIX + CONCERT_PREFIX + concertId;
    }

    /**
     * 🎯 콘서트별 활성 사용자 수 카운트 키 생성
     * @param concertId 콘서트 ID
     * @return Redis 키: `active_users_count:concert:{concertId}`
     */
    public String getActiveUsersCountKey(Long concertId) {
        return ACTIVE_USERS_COUNT_KEY_PREFIX + CONCERT_PREFIX + concertId;
    }

    /**
     * 🎯 사용자별 입장 AccessKey 키 생성
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return Redis 키: `accesskey:concert:{concertId}:user:{userId}`
     */
    public String getAccessKey(Long concertId, Long userId) {
        return ACCESS_KEY_PREFIX + CONCERT_PREFIX + concertId + ":" + USER_PREFIX + userId;
    }

    /**
     * 🎯 사용자별 최종 만료 시각 키 생성
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return Redis 키: `final_expiry:concert:{concertId}:user:{userId}`
     */
    public String getFinalExpiryKey(Long concertId, Long userId) {
        return FINAL_EXPIRY_KEY_PREFIX + CONCERT_PREFIX + concertId + ":" + USER_PREFIX + userId;
    }

    /**
     * 좌석 상태 키 생성 (venue capacity type에 따라 다른 구조)
     *
     * SMALL:  seat:status:{concertId}
     * MEDIUM: seat:status:{concertId}:{grade}
     * LARGE:  seat:status:{concertId}:{grade}:{section}
     */
    public static String getSeatStatusKey(String capacityType, Long concertId,
                                          String grade, String section) {
        if (capacityType == null) {
            return SEAT_STATUS_KEY_PREFIX + concertId;
        }

        return switch (capacityType.toUpperCase()) {
            case "SMALL" -> SEAT_STATUS_KEY_PREFIX + concertId;
            case "MEDIUM" -> SEAT_STATUS_KEY_PREFIX + concertId + ":" + grade;
            case "LARGE" -> SEAT_STATUS_KEY_PREFIX + concertId + ":" + grade + ":" + section;
            default -> SEAT_STATUS_KEY_PREFIX + concertId;
        };
    }

    /**
     * 사용자 선점 좌석 키 생성
     * user:reserved:{concertId}:{userId}
     */
    public static String getUserReservedKey(Long concertId, Long userId) {
        return USER_RESERVED_KEY_PREFIX + concertId + ":" + userId;
    }

    /**
     * 좌석 락 키 생성
     * seat:lock:{concertId}:{seatId}
     */
    public static String getSeatLockKey(Long concertId, Long seatId) {
        return SEAT_LOCK_KEY_PREFIX + concertId + ":" + seatId;
    }

    /**
     * 좌석 TTL 키 생성
     * seat:expire:{concertId}:{seatId}
     */
    public static String getSeatTTLKey(Long concertId, Long seatId) {
        return SEAT_TTL_KEY_PREFIX + concertId + ":" + seatId;
    }
}
