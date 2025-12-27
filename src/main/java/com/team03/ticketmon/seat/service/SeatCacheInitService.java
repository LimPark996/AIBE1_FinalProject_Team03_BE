package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.concert.domain.ConcertSeat;
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
 * 좌석 상태 캐시 초기화 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCacheInitService {

    private final RedissonClient redissonClient;
    private final ConcertSeatRepository concertSeatRepository;
    private static final String SEAT_STATUS_KEY_PREFIX = RedisKeyGenerator.SEAT_STATUS_KEY_PREFIX;

    /**
     * DB 기반 좌석 캐시 초기화
     * ConcertSeat ID 사용으로 ID 매핑 일관성 확보
     */
    @Transactional(readOnly = true)
    public void initializeSeatCacheFromDB(Long concertId) {
        log.info("DB 기반 좌석 캐시 초기화 시작: concertId={}", concertId);

        try {
            // 1. DB 에서 콘서트의 모든 좌석 정보 조회 (Fetch Join 적용)
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            if (concertSeats.isEmpty()) {
                log.warn("콘서트 좌석 데이터가 없습니다: concertId={}", concertId);
                return;
            }

            // 2. DB에 콘서트의 좌석 정보가 있다면, Redis 캐시 구조 준비
            String key = SEAT_STATUS_KEY_PREFIX + concertId;
            RMap<String, SeatStatus> seatMap = redissonClient.getMap(key); // Redis의 Hash를 Java Map 인터페이스로 사용
            // 실행 전 Redis 상태:
            // seat:status:123 -> { "A-1": "AVAILABLE", "A-2": "TEMPORARY" }

            // 기존 캐시 클리어
            seatMap.clear();
            // 실행 후 Redis 상태:
            // seat:status:123 -> { } (빈 Hash, 키는 존재함)

            // 3. 로컬 맵에 모든 좌석 상태 준비 (배치 처리 최적화)
            // HashMap은 Python의 딕셔너리(dict)와 똑같은 로컬 메모리 자료구조
            Map<String, SeatStatus> batchSeatData = new HashMap<>();

            int bookedCount = 0;

            // 콘서트 내 개별 좌석 상태 확인 -> 개별 좌석 상태 객체(SeatStatus) 생성
            for (ConcertSeat concertSeat : concertSeats) { // List<ConcertSeat>을 for 문으로 돌림
                try {
                    // 4. ConcertSeat ID 사용
                    Seat seat = concertSeat.getSeat(); // Seat 객체를 가져옴
                    Long concertSeatId = concertSeat.getConcertSeatId(); // ConcertSeat의 ID 가져옴

                    // 5. 현재 상황에서의 예매 여부는 Ticket 존재 여부와 SeatStatus가 BOOKED 이냐로 판별함
                    boolean isBooked = concertSeat.getTicket() != null; // Ticket이 존재하면 isBooked는 True 이다
                    SeatStatusEnum status = isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE; // isBooked는 True 이면 예매 상태, False 이면 예매 가능 상태임

                    if (isBooked) { // isBooked 가 True 이면
                        bookedCount++; // 예매된 좌석의 수는 1 증가한다
                    }

                    // 6. 좌석 정보 생성
                    String seatInfo = generateSeatInfoFromDB(seat);

                    // 7. SeatStatus(예매 상태인지, 예매 가능 상태인지 등 좌석 상태를 알 수 있음) 객체 생성 (ConcertSeat ID 사용)
                    SeatStatus seatStatus = SeatStatus.builder()
                            .id(concertId + "-" + concertSeatId)
                            .concertId(concertId)
                            .seatId(concertSeatId)                // ConcertSeat ID 사용
                            .status(status)
                            .userId(null) // 초기화 시에는 선점 사용자 없음
                            .reservedAt(null)
                            .expiresAt(null)
                            .seatInfo(seatInfo)
                            .grade(concertSeat.getGrade().name())
                            .price(concertSeat.getPrice())
                            .seatRow(seat.getSeatRow())
                            .seatNumber(seat.getSeatNumber())
                            .section(seat.getSection())
                            .build();

                    // 8. 수정된 키 사용 (ConcertSeat ID로 저장)
                    batchSeatData.put(concertSeatId.toString(), seatStatus);

                } catch (Exception e) {
                    log.error("좌석 상태 생성 중 오류: concertId={}, concertSeat={}",
                            concertId, concertSeat.getConcertSeatId(), e);
                    // 개별 좌석 오류는 스킵하고 계속 처리
                }
            }

            // 9. 한 번의 Redis 호출로 모든 데이터를 seatMap에 일괄 저장
            if (!batchSeatData.isEmpty()) {
                seatMap.putAll(batchSeatData);

                log.info("DB 기반 좌석 캐시 초기화 완료: concertId={}, totalSeats={}, bookedSeats={}, availableSeats={}",
                        concertId, batchSeatData.size(), bookedCount, batchSeatData.size() - bookedCount);
            } else {
                log.warn("처리 가능한 좌석 데이터가 없습니다: concertId={}", concertId);
            }

            // 10. 등급별 available 카운트 초기화
            initializeGradeAvailableCounts(concertId, batchSeatData.values());

            // 11. 구역별 available 카운트 초기화
            initializeSectionAvailableCounts(concertId, batchSeatData.values());

        } catch (Exception e) {
            log.error("DB 기반 좌석 캐시 초기화 중 오류 발생: concertId={}", concertId, e);
            throw new RuntimeException("좌석 캐시 초기화 실패: " + e.getMessage(), e);
        }
    }

    private static final String SEAT_COUNT_KEY_PREFIX = "seat:count:";

    /**
     * 등급별 available 카운트 초기화
     */
    private void initializeGradeAvailableCounts(Long concertId, Collection<SeatStatus> seats) {
        // 등급별 available 좌석 수 계산
        Map<String, Long> availableCountByGrade = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatusEnum.AVAILABLE)
                .collect(Collectors.groupingBy(
                        SeatStatus::getGrade,
                        Collectors.counting()
                ));

        // 등급 목록 추출 (BOOKED 포함한 모든 등급)
        Set<String> allGrades = seats.stream()
                .map(SeatStatus::getGrade)
                .collect(Collectors.toSet());

        // Redis에 카운트 저장
        for (String grade : allGrades) {
            long available = availableCountByGrade.getOrDefault(grade, 0L);

            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + grade + ":available";
            redissonClient.getAtomicLong(countKey).set(available);

            log.debug("등급별 카운트 초기화: concertId={}, grade={}, available={}",
                    concertId, grade, available);
        }
    }

    /**
     * 구역별 available 카운트 초기화
     */
    private void initializeSectionAvailableCounts(Long concertId, Collection<SeatStatus> seats) {
        // 등급+구역별로 그룹핑
        Map<String, Long> availableCountByGradeSection = seats.stream()
                .filter(seat -> seat.getStatus() == SeatStatusEnum.AVAILABLE)
                .filter(seat -> seat.getSection() != null)  // null 방지
                .collect(Collectors.groupingBy(
                        seat -> seat.getGrade() + ":" + seat.getSection(),
                        Collectors.counting()
                ));

        // 모든 등급+구역 조합 추출
        Set<String> allGradeSections = seats.stream()
                .filter(seat -> seat.getSection() != null)  // null 방지
                .map(seat -> seat.getGrade() + ":" + seat.getSection())
                .collect(Collectors.toSet());

        // Redis에 카운트 저장
        for (String gradeSection : allGradeSections) {
            long available = availableCountByGradeSection.getOrDefault(gradeSection, 0L);

            String countKey = SEAT_COUNT_KEY_PREFIX + concertId + ":" + gradeSection + ":available";
            redissonClient.getAtomicLong(countKey).set(available);

            log.debug("구역별 카운트 초기화: concertId={}, gradeSection={}, available={}",
                    concertId, gradeSection, available);
        }
    }

    /**
     * Seat 엔티티로부터 get을 활용해서 좌석 정보 생성
     */
    private String generateSeatInfoFromDB(Seat seat) {
        if (seat == null) { // 정보를 생성할 좌석이 없음
            log.warn("Seat 엔티티가 null 입니다. 해당 seatInfo를 null로 설정합니다.");
            return null;
        }

        String section = seat.getSection() != null ? seat.getSection() : "?"; // Section이 존재하면 Section 정보를 가져오고, 아니라면 "?"을 반환한다.
        String seatRow = seat.getSeatRow() != null ? seat.getSeatRow() : "?"; // SeatRow가 존재하면 SeatRow 정보를 가져오고, 아니라면 "?"을 반환한다.
        Integer seatNumber = seat.getSeatNumber() != null ? seat.getSeatNumber() : 0; // SeatNumber가 존재하면 SeatNumber 정보를 가져오고, 아니라면 "?"을 반환한다.

        return String.format("%s-%s-%d", section, seatRow, seatNumber); // section - seatRow - seatNumber 형태로 정보를 반환한다.
    }

    /**
     * 개선된 캐시 상태 확인
     */
    public Map<String, Object> getCacheStatus(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key); // 특정 콘서트에 대한 캐시 반환

        if (!seatMap.isExists()) { // 좌석 캐시가 존재하지 않으면 반환함
            return Map.of(
                    "concertId", concertId,
                    "cacheKey", key,
                    "cacheExists", false,
                    "message", "캐시가 존재하지 않습니다."
            );
        }

        // 캐시 통계 계산
        Map<String, SeatStatus> allSeats = seatMap.readAllMap();

        long availableSeats = allSeats.values().stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.AVAILABLE ? 1 : 0)
                .sum();

        long reservedSeats = allSeats.values().stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.RESERVED ? 1 : 0)
                .sum();

        long bookedSeats = allSeats.values().stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.BOOKED ? 1 : 0)
                .sum();

        Map<String, Object> status = Map.of( // 좌석 캐시가 하나라도 존재하면 반환함
                "concertId", concertId,
                "cacheKey", key,
                "cacheExists", true,
                "totalSeats", allSeats.size(),
                "availableSeats", availableSeats,
                "reservedSeats", reservedSeats,
                "bookedSeats", bookedSeats,
                "lastUpdated", java.time.LocalDateTime.now()
        );

        log.debug("캐시 상태 조회: {}", status);
        return status;
    }

    /**
     * 특정 콘서트에 대한 모든 좌석 캐시 삭제
     */
    public String clearSeatCache(Long concertId) {
        try {
            String key = SEAT_STATUS_KEY_PREFIX + concertId;
            RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

            if (!seatMap.isExists()) { // 삭제할 좌석 캐시가 아예 존재하지 않은 경우
                log.info("삭제할 좌석 캐시가 존재하지 않음: concertId={}, key={}", concertId, key);
                return "삭제할 캐시가 없습니다.";
            }

            // 삭제할 좌석 캐시가 존재하는 경우
            int seatCount = seatMap.size(); // 삭제할 좌석 수
            boolean deleted = seatMap.delete(); // 삭제 성공 유무 (True or False)

            if (deleted) { // 삭제 성공한 경우 -> 좌석 캐시 삭제 완료 log를 띄움
                log.info("좌석 캐시 삭제 완료: concertId={}, deletedSeats={}", concertId, seatCount);
                return String.format("좌석 캐시 삭제 성공 (삭제된 좌석 수: %d)", seatCount);
            } else { // 삭제 실패한 경우 -> 좌석 캐시 삭제 실패 log를 띄움
                log.warn("좌석 캐시 삭제 실패: concertId={}", concertId);
                return "캐시 삭제에 실패했습니다.";
            }

        } catch (Exception e) {
            log.error("좌석 캐시 삭제 중 오류: concertId={}", concertId, e);
            return "캐시 삭제 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}