package com.team03.ticketmon.seat.scheduler;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.seat.config.SeatProperties;
import com.team03.ticketmon.seat.service.SeatCacheInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SeatCacheWarmupScheduler — 좌석 캐시 사전 예열 스케줄러
 *
 * 이 클래스가 하는 일:
 *   1. 주기적으로(기본 20분) 오픈이 임박한 콘서트를 조회
 *   2. 각 콘서트의 좌석 캐시를 사전 초기화해 오픈 시점의 Cold Start를 방지
 *   3. 다중 인스턴스 환경에서 중복 실행을 막기 위해 Redisson 분산락 사용
 *   4. 이미 처리한 콘서트는 "processed" 키로 마킹해 재실행 시 스킵
 *
 * 정책: SMALL venue만 Eager 초기화 대상이며, MEDIUM/LARGE는 Lazy Loading을 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatCacheWarmupScheduler {

    private final ConcertRepository concertRepository;
    private final SeatCacheInitService seatCacheInitService;
    private final RedissonClient redissonClient;
    private final SeatProperties seatProperties;

    private static final String WARMUP_LOCK_KEY = RedisKeyGenerator.WARMUP_LOCK_KEY;
    private static final String SEAT_PROCESSED_CONCERT_KEY_PREFIX = RedisKeyGenerator.SEAT_PROCESSED_CONCERT_KEY_PREFIX;

    @Scheduled(fixedDelay = 1200000) // 20분마다 실행
    public void autoWarmupSeatCache() {
        RLock lock = redissonClient.getLock(WARMUP_LOCK_KEY);

        try {
            boolean isLocked = lock.tryLock(
                    seatProperties.getLock().getWaitTimeSeconds(),
                    seatProperties.getLock().getLeaseTimeSeconds(),
                    TimeUnit.SECONDS);

            if (!isLocked) {
                log.debug("다른 인스턴스에서 캐시 Warm-up 실행 중. 스킵.");
                return;
            }

            log.info("===== 좌석 캐시 자동 Warm-up 스케줄러 시작 =====");

            LocalDateTime targetTime = LocalDateTime.now()
                    .plusMinutes(seatProperties.getCache().getWarmupMinutesBefore());
            List<Concert> upcomingConcerts = findUpcomingBookingStarts(targetTime);

            log.info("Warm-up 대상 콘서트 개수: {}", upcomingConcerts.size());

            if (upcomingConcerts.isEmpty()) {
                log.info("Warm-up 대상 콘서트가 없습니다.");
                return;
            }

            int successCount = 0;
            int failureCount = 0;

            for (Concert concert : upcomingConcerts) {
                try {
                    if (isAlreadyProcessed(concert.getConcertId())) {
                        log.debug("이미 처리된 콘서트: concertId={}", concert.getConcertId());
                        continue;
                    }

                    String capacityType = concert.getVenueCapacityType();
                    log.info("캐시 Warm-up 시작: concertId={}, title={}, capacityType={}",
                            concert.getConcertId(), concert.getTitle(), capacityType);

                    // ✅ capacityType에 따라 다르게 처리
                    warmupByCapacityType(concert, capacityType);

                    markAsProcessed(concert.getConcertId());
                    successCount++;

                    log.info("캐시 Warm-up 성공: concertId={}, capacityType={}",
                            concert.getConcertId(), capacityType);

                } catch (Exception e) {
                    failureCount++;
                    log.error("캐시 Warm-up 실패: concertId={}, error={}",
                            concert.getConcertId(), e.getMessage(), e);
                }
            }

            log.info("===== 좌석 캐시 Warm-up 완료: 성공={}, 실패={} =====",
                    successCount, failureCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Warm-up 스케줄러 인터럽트", e);
        } catch (Exception e) {
            log.error("Warm-up 스케줄러 예외", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * capacityType에 따라 다른 Warm-up 전략 적용
     */
    private void warmupByCapacityType(Concert concert, String capacityType) {
        Long concertId = concert.getConcertId();

        switch (capacityType != null ? capacityType : "SMALL") {
            case "SMALL" -> {
                // SMALL: 전체 초기화 (기존 방식)
                clearExistingCache(concertId, capacityType);
                seatCacheInitService.initializeSeatCacheFromDB(concertId);
            }
            case "MEDIUM" -> {
                // MEDIUM: Lazy Loading이므로 Warm-up 스킵
                // 또는 인기 등급(VIP, R)만 미리 초기화
                log.info("MEDIUM venue는 Lazy Loading 적용. 선택적 Warm-up: concertId={}", concertId);
                warmupPopularGrades(concertId, capacityType);
            }
            case "LARGE" -> {
                // LARGE: Lazy Loading이므로 Warm-up 스킵
                // 또는 인기 구역만 미리 초기화
                log.info("LARGE venue는 Lazy Loading 적용. 선택적 Warm-up: concertId={}", concertId);
                warmupPopularSections(concertId, capacityType);
            }
            default -> {
                clearExistingCache(concertId, capacityType);
                seatCacheInitService.initializeSeatCacheFromDB(concertId);
            }
        }
    }

    /**
     * MEDIUM: 인기 등급만 미리 초기화
     */
    private void warmupPopularGrades(Long concertId, String capacityType) {
        // VIP, R 등급만 미리 초기화 (가장 인기 있는 등급들)
        List<String> popularGrades = List.of("VIP", "R");

        for (String grade : popularGrades) {
            try {
                String cacheKey = RedisKeyGenerator.getSeatStatusKey(capacityType, concertId, grade, null);
                if (!redissonClient.getMap(cacheKey).isExists()) {
                    seatCacheInitService.initializeSectionCache(concertId, capacityType, grade, null);
                    log.debug("MEDIUM Warm-up 완료: concertId={}, grade={}", concertId, grade);
                }
            } catch (Exception e) {
                log.warn("MEDIUM Warm-up 실패: concertId={}, grade={}, error={}",
                        concertId, grade, e.getMessage());
            }
        }
    }

    /**
     * LARGE: 인기 구역만 미리 초기화
     */
    private void warmupPopularSections(Long concertId, String capacityType) {
        // VIP FLOOR 구역만 미리 초기화 (가장 인기 있는 구역들)
        List<String[]> popularSections = List.of(
                new String[]{"VIP", "FLOOR-A"},
                new String[]{"VIP", "FLOOR-B"},
                new String[]{"R", "FLOOR-A"},
                new String[]{"R", "FLOOR-B"}
        );

        for (String[] gradeSection : popularSections) {
            String grade = gradeSection[0];
            String section = gradeSection[1];

            try {
                String cacheKey = RedisKeyGenerator.getSeatStatusKey(capacityType, concertId, grade, section);
                if (!redissonClient.getMap(cacheKey).isExists()) {
                    seatCacheInitService.initializeSectionCache(concertId, capacityType, grade, section);
                    log.debug("LARGE Warm-up 완료: concertId={}, grade={}, section={}",
                            concertId, grade, section);
                }
            } catch (Exception e) {
                log.warn("LARGE Warm-up 실패: concertId={}, grade={}, section={}, error={}",
                        concertId, grade, section, e.getMessage());
            }
        }
    }

    /**
     * 기존 캐시 삭제 (capacityType에 따라 다른 키 패턴)
     */
    private void clearExistingCache(Long concertId, String capacityType) {
        try {
            // SMALL은 단일 키
            if ("SMALL".equals(capacityType) || capacityType == null) {
                String key = RedisKeyGenerator.SEAT_STATUS_KEY_PREFIX + concertId;
                if (redissonClient.getMap(key).isExists()) {
                    redissonClient.getMap(key).delete();
                    log.debug("SMALL 캐시 삭제: key={}", key);
                }
                return;
            }

            // MEDIUM/LARGE는 패턴 매칭으로 삭제
            String pattern = RedisKeyGenerator.SEAT_STATUS_KEY_PREFIX + concertId + ":*";
            redissonClient.getKeys().deleteByPattern(pattern);
            log.debug("캐시 삭제 (패턴): pattern={}", pattern);

        } catch (Exception e) {
            log.warn("캐시 삭제 실패: concertId={}, error={}", concertId, e.getMessage());
        }
    }

    private List<Concert> findUpcomingBookingStarts(LocalDateTime targetTime) {
        LocalDateTime now = LocalDateTime.now();
        return concertRepository.findUpcomingBookingStarts(now, targetTime);
    }

    private boolean isAlreadyProcessed(Long concertId) {
        String key = SEAT_PROCESSED_CONCERT_KEY_PREFIX + concertId;
        return redissonClient.getBucket(key).isExists();
    }

    private void markAsProcessed(Long concertId) {
        String key = SEAT_PROCESSED_CONCERT_KEY_PREFIX + concertId;
        redissonClient.getBucket(key).set("processed", 24, TimeUnit.HOURS);
    }
}