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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 좌석 상태 캐시 초기화 서비스
 * ✅ 수정사항:
 * - ID 매핑 수정: seat.getSeatId() → concertSeat.getConcertSeatId()
 * - Cache-Aside 패턴 지원
 * - 배치 처리 최적화
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
            // 1. DB에서 콘서트의 모든 좌석 정보 조회 (Fetch Join 적용)
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            if (concertSeats.isEmpty()) {
                log.warn("콘서트 좌석 데이터가 없습니다: concertId={}", concertId);
                return;
            }

            // 2. DB에 콘서트의 좌석 정보가 있다면, Redis 캐시 구조 준비
            String key = SEAT_STATUS_KEY_PREFIX + concertId;
            RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

            // 기존 캐시 클리어
            seatMap.clear();

            // 3. 로컬 맵에 모든 좌석 상태 준비 (배치 처리 최적화)
            Map<String, SeatStatus> batchSeatData = new HashMap<>();
            int bookedCount = 0;

            // 콘서트 내 개별 좌석 상태 확인 -> 개별 좌석 상태 객체(SeatStatus) 생성
            for (ConcertSeat concertSeat : concertSeats) {
                try {
                    // 4. ConcertSeat ID 사용
                    Seat seat = concertSeat.getSeat();
                    Long concertSeatId = concertSeat.getConcertSeatId(); // ConcertSeat의 ID 사용

                    // 5. 현재 상황에서의 예매 여부는 Ticket 존재 여부와 SeatStatus가 BOOKED이냐로 판별함
                    boolean isBooked = concertSeat.getTicket() != null;
                    SeatStatusEnum status = isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE;

                    if (isBooked) {
                        bookedCount++;
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

        } catch (Exception e) {
            log.error("DB 기반 좌석 캐시 초기화 중 오류 발생: concertId={}", concertId, e);
            throw new RuntimeException("좌석 캐시 초기화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Seat 엔티티로부터 get을 활용해서 좌석 정보 생성 - seatInfo
     */
    private String generateSeatInfoFromDB(Seat seat) {
        if (seat == null) {
            log.warn("Seat 엔티티가 null입니다. 해당 seatInfo를 null로 설정합니다.");
            return null;
        }

        String section = seat.getSection() != null ? seat.getSection() : "?";
        String seatRow = seat.getSeatRow() != null ? seat.getSeatRow() : "?";
        Integer seatNumber = seat.getSeatNumber() != null ? seat.getSeatNumber() : 0;

        return String.format("%s-%s-%d", section, seatRow, seatNumber);
    }

    /**
     * ✅ 개선된 캐시 상태 확인
     */
    public Map<String, Object> getCacheStatus(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        if (!seatMap.isExists()) {
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

        Map<String, Object> status = Map.of(
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