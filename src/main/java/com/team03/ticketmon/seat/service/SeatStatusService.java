package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.config.SeatProperties;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.seat.exception.SeatReservationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SeatStatusService — 좌석 상태 관리 핵심 서비스
 *
 * 이 클래스가 하는 일:
 *   1. Redis(Redisson) 기반 좌석 상태 CRUD (AVAILABLE / LOCKED / BOOKED)
 *   2. 콘서트 capacity type(SMALL/MEDIUM/LARGE)에 따른 키 전략 분기
 *      - SMALL: 콘서트 단위로 전체 좌석을 하나의 해시에 저장
 *      - MEDIUM/LARGE: 등급/구역 단위로 분할하여 저장 (Lazy Loading)
 *   3. 사용자별 선점 좌석 Set 관리(getUserReservedSeatIds 등)로 빠른 내 선점 조회 지원
 *   4. 좌석 예매 요청 검증 및 상태 전이(예: AVAILABLE → LOCKED, LOCKED → BOOKED)
 *   5. 상태 변경 시 {@link SeatStatusEventPublisher}를 통한 Pub/Sub 이벤트 발행
 *   6. 캐시 미스 시 {@link SeatCacheInitService}와 연동해 자동/지연 초기화 수행
 *
 * 동작 흐름(예: 예매 선점):
 *   - 컨트롤러에서 예매 요청 수신
 *   - Redisson 분산락 획득으로 동시성 제어
 *   - 현재 상태 확인 → 전이 가능 여부 검증 → 상태 업데이트
 *   - 사용자 선점 Set에 반영 후 만료/변경 이벤트 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStatusService {

    private final RedissonClient redissonClient;
    private final SeatStatusEventPublisher eventPublisher;
    private final SeatCacheInitService seatCacheInitService;
    private final SeatProperties seatProperties;
    private final ConcertSeatRepository concertSeatRepository;
    private final ConcertRepository concertRepository;

    private static final String SEAT_STATUS_KEY_PREFIX = RedisKeyGenerator.SEAT_STATUS_KEY_PREFIX;
    private static final String SEAT_LAST_UPDATE_KEY_PREFIX = RedisKeyGenerator.SEAT_LAST_UPDATE_KEY_PREFIX;
    private static final String SEAT_COUNT_KEY_PREFIX = "seat:count:";

    // ===== 사용자별 선점 좌석 관리 (신규) =====

    /**
     * 사용자 선점 목록에 좌석 추가
     */
    private void addToUserReservedSet(Long concertId, Long userId, Long seatId) {
        String key = RedisKeyGenerator.getUserReservedKey(concertId, userId);
        RSet<Long> userReservedSet = redissonClient.getSet(key);
        userReservedSet.add(seatId);

        // TTL 설정 (선점 시간 + 여유 5분)
        userReservedSet.expire(Duration.ofMinutes(seatProperties.getReservation().getTtlMinutes() + 5));

        log.debug("사용자 선점 목록 추가: userId={}, concertId={}, seatId={}", userId, concertId, seatId);
    }

    /**
     * 사용자 선점 목록에서 좌석 제거
     */
    private void removeFromUserReservedSet(Long concertId, Long userId, Long seatId) {
        String key = RedisKeyGenerator.getUserReservedKey(concertId, userId);
        RSet<Long> userReservedSet = redissonClient.getSet(key);
        userReservedSet.remove(seatId);

        log.debug("사용자 선점 목록 제거: userId={}, concertId={}, seatId={}", userId, concertId, seatId);
    }

    /**
     * 사용자가 선점한 좌석 ID 목록 조회 (빠름! SMEMBERS 최대 6개)
     */
    public Set<Long> getUserReservedSeatIds(Long concertId, Long userId) {
        String key = RedisKeyGenerator.getUserReservedKey(concertId, userId);
        RSet<Long> userReservedSet = redissonClient.getSet(key);
        return userReservedSet.readAll();
    }

    // ===== 좌석 상태 조회 (capacity type별 분기) =====

    /**
     * 개별 좌석 상태 조회 - capacity type에 따라 다른 키 사용
     */
    public Optional<SeatStatus> getSeatStatus(Long concertId, Long concertSeatId) {
        // 1. 콘서트 정보 조회
        Concert concert = concertRepository.findById(concertId).orElse(null);
        if (concert == null) {
            log.warn("콘서트를 찾을 수 없음: concertId={}", concertId);
            return Optional.empty();
        }

        String capacityType = concert.getVenueCapacityType();

        // 2. SMALL은 기존 방식
        if ("SMALL".equals(capacityType) || capacityType == null) {
            return getSeatStatusSmall(concertId, concertSeatId);
        }

        // 3. MEDIUM/LARGE는 좌석 메타데이터 필요
        return getSeatStatusByMetadata(concertId, concertSeatId, capacityType);
    }

    /**
     * SMALL venue용 좌석 상태 조회 (기존 로직)
     */
    private Optional<SeatStatus> getSeatStatusSmall(Long concertId, Long concertSeatId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        SeatStatus status = seatMap.get(concertSeatId.toString());  // HGET O(1)

        // 캐시 미스 시 초기화
        if (status == null && !seatMap.isExists()) {
            log.info("SMALL 캐시 초기화 시작: concertId={}", concertId);
            seatCacheInitService.initializeSeatCacheFromDB(concertId);
            status = seatMap.get(concertSeatId.toString());
        }

        return Optional.ofNullable(status);
    }

    /**
     * MEDIUM/LARGE venue용 좌석 상태 조회
     * 캐시 미스 시 해당 구역만 초기화 (Lazy Loading)
     */
    private Optional<SeatStatus> getSeatStatusByMetadata(Long concertId, Long concertSeatId,
                                                         String capacityType) {
        // 1. DB에서 좌석의 grade, section 조회
        ConcertSeat concertSeat = concertSeatRepository.findByIdWithSeat(concertSeatId).orElse(null);
        if (concertSeat == null) {
            return Optional.empty();
        }

        String grade = concertSeat.getGrade().name();
        String section = concertSeat.getSeat().getSection();

        // 2. 적절한 키로 조회
        String key = RedisKeyGenerator.getSeatStatusKey(capacityType, concertId, grade, section);
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        SeatStatus status = seatMap.get(concertSeatId.toString());

        // 3. 캐시 미스 시 해당 구역만 초기화 (Lazy!)
        if (status == null && !seatMap.isExists()) {
            log.info("Lazy 캐시 초기화: concertId={}, grade={}, section={}",
                    concertId, grade, section);
            seatCacheInitService.initializeSectionCache(concertId, capacityType, grade, section);
            status = seatMap.get(concertSeatId.toString());
        }

        return Optional.ofNullable(status);
    }

    /**
     * 전체 좌석 상태 조회 - SMALL venue 전용 (기존 호환)
     * ⚠️ MEDIUM/LARGE에서는 사용 금지!
     */
    public Map<Long, SeatStatus> getAllSeatStatus(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        Map<String, SeatStatus> rawMap = seatMap.readAllMap();  // HGETALL

        // Cache Miss 시 초기화 (SMALL만!)
        if (rawMap.isEmpty()) {
            log.info("좌석 캐시가 비어있음. 자동 초기화 시작: concertId={}", concertId);
            try {
                seatCacheInitService.initializeSeatCacheFromDB(concertId);
                rawMap = seatMap.readAllMap();
            } catch (Exception e) {
                log.error("좌석 캐시 자동 초기화 실패: concertId={}", concertId, e);
            }
        }

        return rawMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Long.valueOf(entry.getKey()),
                        Map.Entry::getValue
                ));
    }

    /**
     * 현재 좌석 상태 조회 (폴링 응답용)
     * SMALL만 전체 데이터 반환, MEDIUM/LARGE는 등급/구역 파라미터 필요
     */
    public Map<String, Object> getCurrentSeatStatus(Long concertId) {
        return getCurrentSeatStatus(concertId, null, null);
    }

    /**
     * 현재 좌석 상태 조회 (폴링 응답용)
     * 파라미터 없이 호출하면 capacityType에 따라 다르게 동작
     */
    public Map<String, Object> getCurrentSeatStatus(Long concertId, String grade, String section) {
        try {
            Concert concert = concertRepository.findById(concertId).orElse(null);
            if (concert == null) {
                return Map.of("error", "콘서트를 찾을 수 없습니다");
            }

            String capacityType = concert.getVenueCapacityType();
            LocalDateTime lastUpdate = getLastUpdateTime(concertId);

            // SMALL: 전체 좌석 상태 반환 (grade, section 무시)
            if ("SMALL".equals(capacityType) || capacityType == null) {
                return getSmallVenueSeatStatus(concertId, lastUpdate);
            }

            // MEDIUM: grade 필수
            if ("MEDIUM".equals(capacityType)) {
                if (grade == null || grade.isEmpty()) {
                    // 에러가 아니라 안내 메시지
                    return Map.of(
                            "capacityType", "MEDIUM",
                            "hasUpdate", false,
                            "requiresGrade", true,
                            "message", "MEDIUM venue는 등급별 폴링이 필요합니다. grade 파라미터를 추가하세요.",
                            "lastUpdate", lastUpdate != null ? lastUpdate.toString() : ""
                    );
                }
                return getMediumVenueSeatStatus(concertId, grade, lastUpdate);
            }

            // LARGE: grade + section 필수
            if ("LARGE".equals(capacityType)) {
                if (grade == null || grade.isEmpty() || section == null || section.isEmpty()) {
                    return Map.of(
                            "capacityType", "LARGE",
                            "hasUpdate", false,
                            "requiresGradeAndSection", true,
                            "message", "LARGE venue는 구역별 폴링이 필요합니다. grade와 section 파라미터를 추가하세요.",
                            "lastUpdate", lastUpdate != null ? lastUpdate.toString() : ""
                    );
                }
                return getLargeVenueSeatStatus(concertId, grade, section, lastUpdate);
            }

            return Map.of("error", "알 수 없는 capacityType: " + capacityType);

        } catch (Exception e) {
            log.error("현재 좌석 상태 조회 실패: concertId={}", concertId, e);
            return Map.of("error", "조회 실패: " + e.getMessage());
        }
    }

    /**
     * SMALL venue 좌석 상태 조회
     */
    private Map<String, Object> getSmallVenueSeatStatus(Long concertId, LocalDateTime lastUpdate) {
        Map<Long, SeatStatus> allStatus = getAllSeatStatus(concertId);

        // 선점/예매된 좌석만 필터링 (AVAILABLE 제외)
        Map<String, Object> reservedSeats = allStatus.entrySet().stream()
                .filter(e -> e.getValue().getStatus() != SeatStatusEnum.AVAILABLE)
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> createSeatStatusInfo(e.getValue())
                ));

        return Map.of(
                "capacityType", "SMALL",
                "hasUpdate", true,
                "lastUpdate", lastUpdate != null ? lastUpdate.toString() : "",
                "reservedSeats", reservedSeats,
                "totalReserved", reservedSeats.size()
        );
    }

    /**
     * MEDIUM venue 좌석 상태 조회 (등급별)
     */
    private Map<String, Object> getMediumVenueSeatStatus(Long concertId, String grade, LocalDateTime lastUpdate) {
        String cacheKey = RedisKeyGenerator.getSeatStatusKey("MEDIUM", concertId, grade, null);
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(cacheKey);

        if (!seatMap.isExists()) {
            return Map.of(
                    "capacityType", "MEDIUM",
                    "grade", grade,
                    "hasUpdate", false,
                    "message", "캐시가 초기화되지 않았습니다",
                    "lastUpdate", lastUpdate != null ? lastUpdate.toString() : ""
            );
        }

        Map<String, SeatStatus> allStatus = seatMap.readAllMap();

        // 선점/예매된 좌석만 필터링
        Map<String, Object> reservedSeats = allStatus.entrySet().stream()
                .filter(e -> e.getValue().getStatus() != SeatStatusEnum.AVAILABLE)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> createSeatStatusInfo(e.getValue())
                ));

        return Map.of(
                "capacityType", "MEDIUM",
                "grade", grade,
                "hasUpdate", true,
                "lastUpdate", lastUpdate != null ? lastUpdate.toString() : "",
                "reservedSeats", reservedSeats,
                "totalReserved", reservedSeats.size()
        );
    }

    /**
     * LARGE venue 좌석 상태 조회 (등급+구역별)
     */
    private Map<String, Object> getLargeVenueSeatStatus(Long concertId, String grade, String section, LocalDateTime lastUpdate) {
        String cacheKey = RedisKeyGenerator.getSeatStatusKey("LARGE", concertId, grade, section);
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(cacheKey);

        if (!seatMap.isExists()) {
            return Map.of(
                    "capacityType", "LARGE",
                    "grade", grade,
                    "section", section,
                    "hasUpdate", false,
                    "message", "캐시가 초기화되지 않았습니다",
                    "lastUpdate", lastUpdate != null ? lastUpdate.toString() : ""
            );
        }

        Map<String, SeatStatus> allStatus = seatMap.readAllMap();

        // 선점/예매된 좌석만 필터링
        Map<String, Object> reservedSeats = allStatus.entrySet().stream()
                .filter(e -> e.getValue().getStatus() != SeatStatusEnum.AVAILABLE)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> createSeatStatusInfo(e.getValue())
                ));

        return Map.of(
                "capacityType", "LARGE",
                "grade", grade,
                "section", section,
                "hasUpdate", true,
                "lastUpdate", lastUpdate != null ? lastUpdate.toString() : "",
                "reservedSeats", reservedSeats,
                "totalReserved", reservedSeats.size()
        );
    }

    /**
     * 좌석 상태 정보 Map 생성 (폴링 응답용)
     */
    private Map<String, Object> createSeatStatusInfo(SeatStatus seat) {
        Map<String, Object> info = new HashMap<>();
        info.put("status", seat.getStatus().name());
        info.put("seatInfo", seat.getSeatInfo());

        if (seat.getUserId() != null) {
            info.put("userId", seat.getUserId());
        }
        if (seat.getExpiresAt() != null) {
            info.put("expiresAt", seat.getExpiresAt().toString());
        }

        return info;
    }

    /**
     * 좌석 상태 업데이트 (이벤트 발행 없이)
     */
    public void updateSeatStatusWithoutEvent(SeatStatus seatStatus) {
        Concert concert = concertRepository.findById(seatStatus.getConcertId())
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다"));

        String capacityType = concert.getVenueCapacityType();
        String grade = seatStatus.getGrade();
        String section = seatStatus.getSection();

        saveSeatStatus(capacityType, seatStatus.getConcertId(), grade, section, seatStatus);
    }

    /**
     * 좌석 상태 업데이트 (이벤트 발행 포함)
     */
    public void updateSeatStatus(SeatStatus seatStatus) {
        updateSeatStatusWithoutEvent(seatStatus);

        try {
            eventPublisher.publishSeatUpdate(seatStatus);
        } catch (Exception e) {
            log.warn("좌석 상태 이벤트 발행 실패: concertId={}, seatId={}",
                    seatStatus.getConcertId(), seatStatus.getSeatId(), e);
        }
    }
    /**
     * 특정 좌석 ID 목록의 상태만 조회 (HMGET 사용)
     */
    public Map<Long, SeatStatus> getSeatStatusByIds(Long concertId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        Set<String> seatIdStrings = seatIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());

        Map<String, SeatStatus> result = seatMap.getAll(seatIdStrings);  // HMGET

        return result.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Long.valueOf(entry.getKey()),
                        Map.Entry::getValue
                ));
    }

    public Map<Long, SeatStatus> getSeatStatusByIdsFromKey(String key, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return Collections.emptyMap();
        }

        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        Set<String> seatIdStrings = seatIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());

        Map<String, SeatStatus> result = seatMap.getAll(seatIdStrings);

        return result.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Long.valueOf(e.getKey()),
                        Map.Entry::getValue
                ));
    }

    // ===== 사용자 선점 개수 검증 (HGETALL 제거!) =====

    /**
     * 사용자별 좌석 선점 개수 검증 - SMEMBERS 사용 (빠름!)
     */
    private void validateUserSeatReservationLimit(Long concertId, Long userId, Long targetSeatId) {
        // 변경 전: getAllSeatStatus() → HGETALL 50,000건 ❌
        // 변경 후: getUserReservedSeatIds() → SMEMBERS 최대 6건 ✅

        Set<Long> userReservedSeatIds = getUserReservedSeatIds(concertId, userId);

        long currentReservationCount = userReservedSeatIds.stream()
                .filter(id -> !id.equals(targetSeatId))
                .count();

        int maxSeatCount = seatProperties.getReservation().getMaxSeatCount();
        if (currentReservationCount >= maxSeatCount) {
            log.warn("사용자 좌석 선점 개수 제한 초과: userId={}, concertId={}, currentCount={}, maxLimit={}",
                    userId, concertId, currentReservationCount, maxSeatCount);

            throw new SeatReservationException(
                    String.format("좌석 선점은 최대 %d개까지만 가능합니다. 현재 선점 좌석: %d개",
                            maxSeatCount, currentReservationCount)
            );
        }

        log.debug("사용자 좌석 선점 개수 검증 통과: userId={}, currentCount={}, maxLimit={}",
                userId, currentReservationCount, maxSeatCount);
    }

    /**
     * 좌석 예매 확정 (RESERVED → BOOKED)
     * 결제 완료 후 호출됨
     */
    public void bookSeat(Long concertId, Long concertSeatId) {
        log.info("좌석 예매 확정 시작: concertId={}, seatId={}", concertId, concertSeatId);

        // 1. 현재 좌석 상태 조회
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, concertSeatId);

        if (currentStatus.isEmpty()) {
            log.warn("존재하지 않는 좌석 예매 확정 시도: concertId={}, seatId={}", concertId, concertSeatId);
            throw new SeatReservationException("존재하지 않는 좌석입니다.");
        }

        SeatStatus currentSeat = currentStatus.get();

        // 2. 이미 BOOKED인 경우 스킵
        if (currentSeat.getStatus() == SeatStatusEnum.BOOKED) {
            log.info("이미 예매 완료된 좌석: concertId={}, seatId={}", concertId, concertSeatId);
            return;
        }

        // 3. RESERVED 상태가 아니면 에러
        if (currentSeat.getStatus() != SeatStatusEnum.RESERVED) {
            log.warn("예매 확정 불가능한 좌석 상태: concertId={}, seatId={}, status={}",
                    concertId, concertSeatId, currentSeat.getStatus());
            throw new SeatReservationException("선점되지 않은 좌석은 예매할 수 없습니다.");
        }

        // 4. 콘서트 정보 조회
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다"));
        String capacityType = concert.getVenueCapacityType();

        // 5. BOOKED 상태로 변경
        SeatStatus bookedSeat = SeatStatus.builder()
                .id(currentSeat.getId())
                .concertId(concertId)
                .seatId(concertSeatId)
                .status(SeatStatusEnum.BOOKED)  // ✅ BOOKED로 변경
                .userId(currentSeat.getUserId())
                .reservedAt(currentSeat.getReservedAt())
                .expiresAt(null)  // BOOKED는 만료 없음
                .seatInfo(currentSeat.getSeatInfo())
                .grade(currentSeat.getGrade())
                .price(currentSeat.getPrice())
                .seatRow(currentSeat.getSeatRow())
                .seatNumber(currentSeat.getSeatNumber())
                .section(currentSeat.getSection())
                .build();

        // 6. Redis 저장
        saveSeatStatus(capacityType, concertId, currentSeat.getGrade(),
                currentSeat.getSection(), bookedSeat);

        // 7. 사용자 선점 목록에서 제거 (BOOKED 되면 선점 목록에서 제외)
        if (currentSeat.getUserId() != null) {
            removeFromUserReservedSet(concertId, currentSeat.getUserId(), concertSeatId);
        }

        // 8. TTL 키 삭제 (BOOKED는 만료되지 않음)
        removeSeatTTLKey(concertId, concertSeatId);

        // 9. 이벤트 발행
        try {
            eventPublisher.publishSeatUpdate(bookedSeat);
        } catch (Exception e) {
            log.warn("좌석 예매 확정 이벤트 발행 실패: concertId={}, seatId={}", concertId, concertSeatId, e);
        }

        log.info("좌석 예매 확정 완료: concertId={}, seatId={}, userId={}",
                concertId, concertSeatId, currentSeat.getUserId());
    }

    // ===== 좌석 선점 (수정됨) =====

    @Transactional
    public SeatStatus reserveSeat(Long concertId, Long concertSeatId, Long userId, String seatInfo) {
        String lockKey = RedisKeyGenerator.getSeatLockKey(concertId, concertSeatId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(
                    seatProperties.getLock().getWaitTimeSeconds(),
                    seatProperties.getLock().getLeaseTimeSeconds(),
                    TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("좌석 락 획득 실패: concertId={}, seatId={}, userId={}",
                        concertId, concertSeatId, userId);
                throw new SeatReservationException("다른 사용자가 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }

            log.debug("좌석 락 획득 성공: concertId={}, seatId={}, userId={}",
                    concertId, concertSeatId, userId);

            // === 임계 구역 시작 ===

            // 1. 콘서트 정보 조회
            Concert concert = concertRepository.findById(concertId)
                    .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다: " + concertId));
            String capacityType = concert.getVenueCapacityType();

            // 2. 좌석 메타데이터 조회
            ConcertSeat concertSeat = concertSeatRepository.findByIdWithSeat(concertSeatId)
                    .orElseThrow(() -> new SeatReservationException("존재하지 않는 좌석입니다."));

            String grade = concertSeat.getGrade().name();
            String section = concertSeat.getSeat().getSection();

            // 3. 현재 좌석 상태 확인
            Optional<SeatStatus> currentStatus = getSeatStatus(concertId, concertSeatId);

            if (currentStatus.isPresent()) {
                SeatStatus seat = currentStatus.get();

                if (seat.getStatus() == SeatStatusEnum.BOOKED) {
                    throw new SeatReservationException("이미 예매 완료된 좌석입니다.");
                }

                if (seat.getStatus() == SeatStatusEnum.RESERVED) {
                    if (!seat.isExpired()) {
                        if (userId.equals(seat.getUserId())) {
                            log.info("동일 사용자의 좌석 재선점 요청: concertId={}, seatId={}, userId={}",
                                    concertId, concertSeatId, userId);
                            return seat;
                        } else {
                            throw new SeatReservationException("다른 사용자가 선점 중인 좌석입니다.");
                        }
                    }
                    log.info("만료된 선점 좌석 재선점: concertId={}, seatId={}", concertId, concertSeatId);
                }
            }

            // 4. 사용자별 좌석 선점 개수 검증 (HGETALL 제거됨!)
            validateUserSeatReservationLimit(concertId, userId, concertSeatId);

            // 5. 새로운 선점 상태 생성
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusMinutes(seatProperties.getReservation().getTtlMinutes());

            SeatStatus reserved = SeatStatus.builder()
                    .id(concertId + "-" + concertSeatId)
                    .concertId(concertId)
                    .seatId(concertSeatId)
                    .status(SeatStatusEnum.RESERVED)
                    .userId(userId)
                    .reservedAt(now)
                    .expiresAt(expiresAt)
                    .seatInfo(seatInfo)
                    .grade(grade)
                    .price(concertSeat.getPrice())
                    .seatRow(concertSeat.getSeat().getSeatRow())
                    .seatNumber(concertSeat.getSeat().getSeatNumber())
                    .section(section)
                    .build();

            // 6. Redis에 저장 (capacity type에 따라 다른 키!)
            saveSeatStatus(capacityType, concertId, grade, section, reserved);

            // 7. 사용자 선점 목록에 추가 (신규!)
            addToUserReservedSet(concertId, userId, concertSeatId);

            // 8. 카운트 감소
            decrementAvailableCount(concertId, grade);
            decrementSectionAvailableCount(concertId, grade, section);

            // 9. TTL 키 생성
            createSeatTTLKey(concertId, concertSeatId);

            // 10. 이벤트 발행
            try {
                eventPublisher.publishSeatUpdate(reserved);
            } catch (Exception e) {
                log.warn("좌석 상태 이벤트 발행 실패: concertId={}, seatId={}", concertId, concertSeatId, e);
            }

            log.info("좌석 선점 완료: concertId={}, seatId={}, userId={}, capacityType={}, expiresAt={}",
                    concertId, concertSeatId, userId, capacityType, expiresAt);

            return reserved;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("좌석 선점 중 인터럽트: concertId={}, seatId={}, userId={}",
                    concertId, concertSeatId, userId, e);
            throw new SeatReservationException("좌석 선점 처리가 중단되었습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("좌석 락 해제: concertId={}, seatId={}, userId={}",
                        concertId, concertSeatId, userId);
            }
        }
    }

    /**
     * 좌석 상태 저장 (capacity type에 따라 다른 키)
     */
    private void saveSeatStatus(String capacityType, Long concertId, String grade,
                                String section, SeatStatus seatStatus) {
        String key = RedisKeyGenerator.getSeatStatusKey(capacityType, concertId, grade, section);
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);
        seatMap.put(seatStatus.getSeatId().toString(), seatStatus);

        updateLastUpdateTime(concertId);

        log.debug("좌석 상태 저장: key={}, seatId={}, status={}",
                key, seatStatus.getSeatId(), seatStatus.getStatus());
    }

    // ===== 좌석 해제 (수정됨) =====

    public void releaseSeat(Long concertId, Long concertSeatId, Long userId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, concertSeatId);

        if (!currentStatus.isPresent()) {
            log.warn("존재하지 않는 좌석 해제 시도: concertId={}, seatId={}, userId={}",
                    concertId, concertSeatId, userId);
            throw new SeatReservationException("존재하지 않는 좌석입니다.");
        }

        SeatStatus currentSeat = currentStatus.get();

        // 상태 검증
        if (!currentSeat.isReserved() && currentSeat.getStatus() != SeatStatusEnum.BOOKED) {
            log.warn("해제 불가능한 좌석 상태: concertId={}, seatId={}, status={}",
                    concertId, concertSeatId, currentSeat.getStatus());
            throw new SeatReservationException("해제할 수 없는 좌석 상태입니다: " + currentSeat.getStatus());
        }

        // 권한 검증
        if (!userId.equals(currentSeat.getUserId())) {
            log.warn("권한 없는 좌석 해제: concertId={}, seatId={}, requestUserId={}, ownerUserId={}",
                    concertId, concertSeatId, userId, currentSeat.getUserId());
            throw new SeatReservationException("다른 사용자가 선점한 좌석은 해제할 수 없습니다.");
        }

        // 콘서트 정보 조회
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다"));
        String capacityType = concert.getVenueCapacityType();

        // 새 상태 생성
        SeatStatus updatedStatus = SeatStatus.builder()
                .id(concertId + "-" + concertSeatId)
                .concertId(concertId)
                .seatId(concertSeatId)
                .status(SeatStatusEnum.AVAILABLE)
                .userId(null)
                .reservedAt(null)
                .expiresAt(null)
                .seatInfo(currentSeat.getSeatInfo())
                .grade(currentSeat.getGrade())
                .price(currentSeat.getPrice())
                .seatRow(currentSeat.getSeatRow())
                .seatNumber(currentSeat.getSeatNumber())
                .section(currentSeat.getSection())
                .build();

        // Redis 저장 (capacity type에 따라 다른 키)
        saveSeatStatus(capacityType, concertId, currentSeat.getGrade(),
                currentSeat.getSection(), updatedStatus);

        // 사용자 선점 목록에서 제거 (신규!)
        removeFromUserReservedSet(concertId, userId, concertSeatId);

        // 카운트 증가
        incrementAvailableCount(concertId, currentSeat.getGrade());
        incrementSectionAvailableCount(concertId, currentSeat.getGrade(), currentSeat.getSection());

        // TTL 키 삭제
        removeSeatTTLKey(concertId, concertSeatId);

        // 이벤트 발행
        try {
            eventPublisher.publishSeatUpdate(updatedStatus);
        } catch (Exception e) {
            log.warn("좌석 상태 이벤트 발행 실패: concertId={}, seatId={}", concertId, concertSeatId, e);
        }

        log.info("좌석 선점 해제 완료: concertId={}, seatId={}, userId={}",
                concertId, concertSeatId, userId);
    }

    // ===== 사용자 선점 좌석 조회 (HGETALL 제거!) =====

    /**
     * 특정 사용자가 선점한 좌석 목록 조회
     */
    public List<SeatStatus> getUserReservedSeats(Long concertId, Long userId) {
        // 1. 사용자 선점 좌석 ID 조회 (빠름!)
        Set<Long> seatIds = getUserReservedSeatIds(concertId, userId);

        if (seatIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 해당 좌석들의 상태만 조회
        return seatIds.stream()
                .map(seatId -> getSeatStatus(concertId, seatId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(SeatStatus::isReserved)
                .collect(Collectors.toList());
    }

    // ===== TTL 관리 =====

    private void createSeatTTLKey(Long concertId, Long concertSeatId) {
        try {
            String ttlKey = RedisKeyGenerator.getSeatTTLKey(concertId, concertSeatId);
            RBucket<String> bucket = redissonClient.getBucket(ttlKey);
            bucket.set("reserved", seatProperties.getReservation().getTtlMinutes(), TimeUnit.MINUTES);
            log.debug("좌석 TTL 키 생성: key={}, ttl={}분", ttlKey, seatProperties.getReservation().getTtlMinutes());
        } catch (Exception e) {
            log.error("좌석 TTL 키 생성 실패: concertId={}, seatId={}", concertId, concertSeatId, e);
        }
    }

    private void removeSeatTTLKey(Long concertId, Long concertSeatId) {
        try {
            String ttlKey = RedisKeyGenerator.getSeatTTLKey(concertId, concertSeatId);
            RBucket<String> bucket = redissonClient.getBucket(ttlKey);
            bucket.delete();
            log.debug("좌석 TTL 키 삭제: key={}", ttlKey);
        } catch (Exception e) {
            log.error("좌석 TTL 키 삭제 실패: concertId={}, seatId={}", concertId, concertSeatId, e);
        }
    }

    // ===== 카운트 관리 =====

    private void decrementAvailableCount(Long concertId, String grade) {
        if (grade == null) return;
        try {
            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade + ":available";
            redissonClient.getAtomicLong(countKey).decrementAndGet();
        } catch (Exception e) {
            log.warn("available 카운트 감소 실패: concertId={}, grade={}", concertId, grade, e);
        }
    }

    private void incrementAvailableCount(Long concertId, String grade) {
        if (grade == null) return;
        try {
            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade + ":available";
            redissonClient.getAtomicLong(countKey).incrementAndGet();
        } catch (Exception e) {
            log.warn("available 카운트 증가 실패: concertId={}, grade={}", concertId, grade, e);
        }
    }

    private void decrementSectionAvailableCount(Long concertId, String grade, String section) {
        if (grade == null || section == null) return;
        try {
            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade + ":" + section + ":available";
            redissonClient.getAtomicLong(countKey).decrementAndGet();
        } catch (Exception e) {
            log.warn("구역 available 카운트 감소 실패: concertId={}, grade={}, section={}",
                    concertId, grade, section, e);
        }
    }

    private void incrementSectionAvailableCount(Long concertId, String grade, String section) {
        if (grade == null || section == null) return;
        try {
            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade + ":" + section + ":available";
            redissonClient.getAtomicLong(countKey).incrementAndGet();
        } catch (Exception e) {
            log.warn("구역 available 카운트 증가 실패: concertId={}, grade={}, section={}",
                    concertId, grade, section, e);
        }
    }

    private void updateLastUpdateTime(Long concertId) {
        try {
            String key = SEAT_LAST_UPDATE_KEY_PREFIX + concertId;
            RBucket<LocalDateTime> bucket = redissonClient.getBucket(key);
            bucket.set(LocalDateTime.now(), seatProperties.getReservation().getLastUpdateTtlHours(), TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("마지막 업데이트 시간 설정 실패: concertId={}", concertId, e);
        }
    }

    /**
     * 콘서트의 마지막 업데이트 시간 조회
     */
    public LocalDateTime getLastUpdateTime(Long concertId) {
        try {
            String key = SEAT_LAST_UPDATE_KEY_PREFIX + concertId;
            RBucket<LocalDateTime> bucket = redissonClient.getBucket(key);
            return bucket.get();
        } catch (Exception e) {
            log.warn("마지막 업데이트 시간 조회 실패: concertId={}", concertId, e);
            return null;
        }
    }

    // ===== 관리자용 기능 =====

    public void forceReleaseSeat(Long concertId, Long concertSeatId) {
        Optional<SeatStatus> currentStatus = getSeatStatus(concertId, concertSeatId);

        if (currentStatus.isPresent()) {
            SeatStatus currentSeat = currentStatus.get();
            Long previousUserId = currentSeat.getUserId();

            Concert concert = concertRepository.findById(concertId)
                    .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다"));
            String capacityType = concert.getVenueCapacityType();

            SeatStatus updatedStatus = SeatStatus.builder()
                    .id(concertId + "-" + concertSeatId)
                    .concertId(concertId)
                    .seatId(concertSeatId)
                    .status(SeatStatusEnum.AVAILABLE)
                    .userId(null)
                    .reservedAt(null)
                    .expiresAt(null)
                    .seatInfo(currentSeat.getSeatInfo())
                    .grade(currentSeat.getGrade())
                    .section(currentSeat.getSection())
                    .build();

            saveSeatStatus(capacityType, concertId, currentSeat.getGrade(),
                    currentSeat.getSection(), updatedStatus);

            // 사용자 선점 목록에서도 제거
            if (previousUserId != null) {
                removeFromUserReservedSet(concertId, previousUserId, concertSeatId);
            }

            incrementAvailableCount(concertId, currentSeat.getGrade());
            incrementSectionAvailableCount(concertId, currentSeat.getGrade(), currentSeat.getSection());
            removeSeatTTLKey(concertId, concertSeatId);

            log.info("좌석 강제 해제 완료 (관리자): concertId={}, seatId={}, previousUserId={}",
                    concertId, concertSeatId, previousUserId);
        }
    }
}