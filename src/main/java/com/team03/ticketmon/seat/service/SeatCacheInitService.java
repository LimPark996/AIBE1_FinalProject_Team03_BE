package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.venue.domain.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SeatCacheInitService — 좌석 캐시 초기화 서비스
 *
 * 이 클래스가 하는 일:
 *   1. DB의 ConcertSeat 데이터를 Redis 좌석 상태 캐시로 로드
 *   2. venue capacity type에 따라 초기화 전략 분기
 *      - SMALL: 콘서트 전체 좌석을 한 번에 초기화 (Eager)
 *      - MEDIUM/LARGE: 사용자가 접근한 등급/구역만 부분 초기화 (Lazy Loading)
 *   3. 초기화 시 좌석 수 카운터 및 메타 정보 함께 세팅
 *
 * {@link SeatStatusService}, {@link SeatLayoutService}의 캐시 미스 복구 경로와
 * {@link com.team03.ticketmon.seat.scheduler.SeatCacheWarmupScheduler}에서 호출된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCacheInitService {

    private final RedissonClient redissonClient;
    private final ConcertSeatRepository concertSeatRepository;
    private final ConcertRepository concertRepository;

    private static final String SEAT_STATUS_KEY_PREFIX = RedisKeyGenerator.SEAT_STATUS_KEY_PREFIX;
    private static final String SEAT_COUNT_KEY_PREFIX = "seat:count:";

    /**
     * DB 기반 좌석 캐시 초기화 - capacity type에 따라 분기
     */
    @Transactional(readOnly = true)
    public void initializeSeatCacheFromDB(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다: " + concertId));

        String capacityType = concert.getVenueCapacityType();

        log.info("좌석 캐시 초기화 시작: concertId={}, capacityType={}", concertId, capacityType);

        // SMALL만 전체 초기화, MEDIUM/LARGE는 Lazy Loading
        if ("SMALL".equals(capacityType) || capacityType == null) {
            initializeSmallVenue(concertId);
        } else {
            // MEDIUM/LARGE는 여기서 아무것도 안 함!
            // 사용자가 특정 등급/구역 접근 시 initializeSectionCache()가 호출됨
            log.info("MEDIUM/LARGE venue는 Lazy Loading 적용: concertId={}", concertId);
        }
    }

    /**
     * 특정 구역만 초기화 (MEDIUM/LARGE용)
     */
    @Transactional(readOnly = true)
    public void initializeSectionCache(Long concertId, String capacityType, String grade, String section) {
        log.info("구역 캐시 초기화: concertId={}, capacityType={}, grade={}, section={}",
                concertId, capacityType, grade, section);

        List<ConcertSeat> seats;

        if ("MEDIUM".equals(capacityType)) {
            seats = concertSeatRepository.findByConcertIdAndGrade(concertId, SeatGrade.valueOf(grade));
        } else {
            seats = concertSeatRepository.findByConcertIdAndGradeAndSection(
                    concertId, SeatGrade.valueOf(grade), section);
        }

        if (seats.isEmpty()) {
            log.warn("초기화할 좌석이 없음: concertId={}, grade={}, section={}", concertId, grade, section);
            return;
        }

        String key = RedisKeyGenerator.getSeatStatusKey(capacityType, concertId, grade, section);
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        Map<String, SeatStatus> batchData = seats.stream()
                .collect(Collectors.toMap(
                        cs -> cs.getConcertSeatId().toString(),
                        cs -> createSeatStatus(concertId, cs)
                ));

        seatMap.putAll(batchData);

        initializeSectionCounts(concertId, grade, section, batchData.values());

        log.info("구역 캐시 초기화 완료: key={}, 좌석수={}", key, batchData.size());
    }

    /**
     * 구역 초기화 시 카운트도 초기화 (신규 추가)
     */
    private void initializeSectionCounts(Long concertId, String grade, String section, Collection<SeatStatus> seats) {
        long availableCount = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatusEnum.AVAILABLE)
                .count();

        // 구역별 카운트 초기화
        String sectionCountKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade + ":" + section + ":available";
        redissonClient.getAtomicLong(sectionCountKey).set(availableCount);
        log.info("구역 카운트 초기화: key={}, count={}", sectionCountKey, availableCount);

        // 등급별 카운트도 업데이트 (누적)
        String gradeCountKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade + ":available";
        redissonClient.getAtomicLong(gradeCountKey).addAndGet(availableCount);
        log.info("등급 카운트 누적: key={}, added={}", gradeCountKey, availableCount);
    }

    /**
     * SMALL venue 초기화 - 단일 Hash (기존 방식)
     */
    private void initializeSmallVenue(Long concertId) {
        List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

        if (concertSeats.isEmpty()) {
            log.warn("좌석 데이터 없음: concertId={}", concertId);
            return;
        }

        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);
        seatMap.clear();

        Map<String, SeatStatus> batchData = concertSeats.stream()
                .collect(Collectors.toMap(
                        cs -> cs.getConcertSeatId().toString(),
                        cs -> createSeatStatus(concertId, cs)
                ));

        seatMap.putAll(batchData);

        // 등급별/구역별 카운트 초기화
        initializeGradeAvailableCounts(concertId, batchData.values());
        initializeSectionAvailableCounts(concertId, batchData.values());

        log.info("SMALL venue 캐시 초기화 완료: concertId={}, 좌석수={}", concertId, batchData.size());
    }

    /**
     * SeatStatus 객체 생성
     */
    private SeatStatus createSeatStatus(Long concertId, ConcertSeat cs) {
        Seat seat = cs.getSeat();
        boolean isBooked = cs.getTicket() != null;

        return SeatStatus.builder()
                .id(concertId + "-" + cs.getConcertSeatId())
                .concertId(concertId)
                .seatId(cs.getConcertSeatId())
                .status(isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE)
                .userId(null)
                .reservedAt(null)
                .expiresAt(null)
                .seatInfo(generateSeatInfo(seat))
                .grade(cs.getGrade().name())
                .price(cs.getPrice())
                .seatRow(seat.getSeatRow())
                .seatNumber(seat.getSeatNumber())
                .section(seat.getSection())
                .build();
    }

    private String generateSeatInfo(Seat seat) {
        return String.format("%s-%s-%d",
                seat.getSection() != null ? seat.getSection() : "?",
                seat.getSeatRow() != null ? seat.getSeatRow() : "?",
                seat.getSeatNumber() != null ? seat.getSeatNumber() : 0);
    }

    /**
     * 등급별 available 카운트 초기화
     */
    private void initializeGradeAvailableCounts(Long concertId, Collection<SeatStatus> seats) {
        Map<String, Long> availableCountByGrade = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatusEnum.AVAILABLE)
                .collect(Collectors.groupingBy(SeatStatus::getGrade, Collectors.counting()));

        Set<String> allGrades = seats.stream()
                .map(SeatStatus::getGrade)
                .collect(Collectors.toSet());

        for (String grade : allGrades) {
            long available = availableCountByGrade.getOrDefault(grade, 0L);
            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade + ":available";
            redissonClient.getAtomicLong(countKey).set(available);
        }
    }

    /**
     * 구역별 available 카운트 초기화
     */
    private void initializeSectionAvailableCounts(Long concertId, Collection<SeatStatus> seats) {
        Map<String, Long> availableCountByGradeSection = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatusEnum.AVAILABLE)
                .filter(seat -> seat.getSection() != null)
                .collect(Collectors.groupingBy(
                        seat -> seat.getGrade() + ":" + seat.getSection(),
                        Collectors.counting()
                ));

        Set<String> allGradeSections = seats.stream()
                .filter(seat -> seat.getSection() != null)
                .map(seat -> seat.getGrade() + ":" + seat.getSection())
                .collect(Collectors.toSet());

        for (String gradeSection : allGradeSections) {
            long available = availableCountByGradeSection.getOrDefault(gradeSection, 0L);
            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + gradeSection + ":available";
            redissonClient.getAtomicLong(countKey).set(available);
        }
    }

    /**
     * 좌석 캐시 삭제 (capacityType에 따라 다른 키 패턴)
     */
    public String clearSeatCache(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다: " + concertId));

        String capacityType = concert.getVenueCapacityType();

        log.info("좌석 캐시 삭제 시작: concertId={}, capacityType={}", concertId, capacityType);

        long deletedCount = 0;

        try {
            if ("SMALL".equals(capacityType) || capacityType == null) {
                // SMALL: 단일 키 삭제
                String key = SEAT_STATUS_KEY_PREFIX + concertId;
                boolean deleted = redissonClient.getMap(key).delete();
                deletedCount = deleted ? 1 : 0;
            } else {
                // MEDIUM/LARGE: 패턴 매칭으로 관련 키 전부 삭제
                String pattern = SEAT_STATUS_KEY_PREFIX + concertId + ":*";
                deletedCount = redissonClient.getKeys().deleteByPattern(pattern);
            }

            // 카운트 키도 삭제
            String countPattern = SEAT_COUNT_KEY_PREFIX + concertId + ":*";
            long countDeleted = redissonClient.getKeys().deleteByPattern(countPattern);

            // 사용자 선점 키도 삭제
            String userReservedPattern = "user:reserved:" + concertId + ":*";
            long userReservedDeleted = redissonClient.getKeys().deleteByPattern(userReservedPattern);

            log.info("좌석 캐시 삭제 완료: concertId={}, 상태키={}, 카운트키={}, 사용자선점키={}",
                    concertId, deletedCount, countDeleted, userReservedDeleted);

            return String.format("캐시 삭제 완료 (상태: %d, 카운트: %d, 사용자선점: %d)",
                    deletedCount, countDeleted, userReservedDeleted);

        } catch (Exception e) {
            log.error("좌석 캐시 삭제 실패: concertId={}, error={}", concertId, e.getMessage(), e);
            throw new RuntimeException("캐시 삭제 실패: " + e.getMessage());
        }
    }

    /**
     * 전체 좌석 캐시 삭제 (모든 콘서트)
     */
    public String clearAllSeatCache() {
        log.info("전체 좌석 캐시 삭제 시작");

        try {
            // 모든 좌석 상태 키 삭제
            String statusPattern = SEAT_STATUS_KEY_PREFIX + "*";
            long statusDeleted = redissonClient.getKeys().deleteByPattern(statusPattern);

            // 모든 카운트 키 삭제
            String countPattern = SEAT_COUNT_KEY_PREFIX + "*";
            long countDeleted = redissonClient.getKeys().deleteByPattern(countPattern);

            // 모든 사용자 선점 키 삭제
            String userReservedPattern = "user:reserved:*";
            long userReservedDeleted = redissonClient.getKeys().deleteByPattern(userReservedPattern);

            log.info("전체 좌석 캐시 삭제 완료: 상태키={}, 카운트키={}, 사용자선점키={}",
                    statusDeleted, countDeleted, userReservedDeleted);

            return String.format("전체 캐시 삭제 완료 (상태: %d, 카운트: %d, 사용자선점: %d)",
                    statusDeleted, countDeleted, userReservedDeleted);

        } catch (Exception e) {
            log.error("전체 좌석 캐시 삭제 실패: error={}", e.getMessage(), e);
            throw new RuntimeException("전체 캐시 삭제 실패: " + e.getMessage());
        }
    }
}