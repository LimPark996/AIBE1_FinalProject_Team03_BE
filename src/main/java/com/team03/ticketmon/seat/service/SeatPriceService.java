package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.dto.SeatPriceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 좌석 가격 정보 조회 서비스
 * 콘서트별 좌석 가격 정보를 제공하는 읽기 전용 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatPriceService {

    private final ConcertSeatRepository concertSeatRepository;
    private final ConcertRepository concertRepository;

    /**
     * 특정 콘서트의 모든 좌석 가격 정보를 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @return 좌석 가격 정보 목록
     * @throws BusinessException 콘서트가 존재하지 않을 때
     */
    @Transactional(readOnly = true)
    public List<SeatPriceResponse> getConcertSeatPrices(Long concertId) {
        log.debug("콘서트 전체 좌석 가격 조회 시작: concertId={}", concertId);

        // 1. 콘서트 존재 확인
        validateConcertExists(concertId);

        // 2. 콘서트의 모든 좌석 조회 (Seat 정보와 함께 페치)
        List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertConcertIdWithSeat(concertId);

        // 3. DTO로 변환
        List<SeatPriceResponse> responses = concertSeats.stream()
                .map(SeatPriceResponse::from)
                .toList();

        log.info("콘서트 전체 좌석 가격 조회 완료: concertId={}, seatCount={}", concertId, responses.size());
        return responses;
    }

    /**
     * 선택된 좌석들의 가격 정보를 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @param seatIds   조회할 좌석 ID 목록
     * @return 선택된 좌석들의 가격 정보 목록
     * @throws BusinessException 콘서트가 존재하지 않거나 일부 좌석을 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public List<SeatPriceResponse> getSelectedSeatPrices(Long concertId, List<Long> seatIds) {
        log.debug("선택된 좌석 가격 조회 시작: concertId={}, seatIds={}", concertId, seatIds);

        // 1. 입력 값 검증
        if (seatIds == null || seatIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "좌석 ID 목록이 비어있습니다.");
        }

        // 2. 콘서트 존재 확인
        validateConcertExists(concertId);

        // 3. 선택된 좌석들 조회
        List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertConcertIdAndConcertSeatIdInWithSeat(
                concertId, seatIds);

        // 4. 요청한 좌석 수와 실제 조회된 좌석 수 비교
        if (concertSeats.size() != seatIds.size()) {
            log.warn("일부 좌석을 찾을 수 없음: concertId={}, requested={}, found={}",
                    concertId, seatIds.size(), concertSeats.size());

            // 찾지 못한 좌석 ID들을 로그로 남김
            List<Long> foundSeatIds = concertSeats.stream()
                    .map(ConcertSeat::getConcertSeatId)
                    .toList();

            List<Long> missingSeatIds = seatIds.stream()
                    .filter(id -> !foundSeatIds.contains(id))
                    .toList();

            log.warn("찾을 수 없는 좌석 ID들: {}", missingSeatIds);

            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                    String.format("일부 좌석을 찾을 수 없습니다. (요청: %d개, 조회: %d개)",
                            seatIds.size(), concertSeats.size()));
        }

        // 5. DTO로 변환
        List<SeatPriceResponse> responses = concertSeats.stream()
                .map(SeatPriceResponse::from)
                .toList();

        log.info("선택된 좌석 가격 조회 완료: concertId={}, requestedSeats={}, foundSeats={}",
                concertId, seatIds.size(), responses.size());

        return responses;
    }

    /**
     * 콘서트 존재 여부를 검증합니다.
     *
     * @param concertId 검증할 콘서트 ID
     * @throws BusinessException 콘서트가 존재하지 않을 때
     */
    private void validateConcertExists(Long concertId) {
        if (!concertRepository.existsById(concertId)) {
            log.warn("존재하지 않는 콘서트 ID: {}", concertId);
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND,
                    "콘서트를 찾을 수 없습니다: " + concertId);
        }
    }
}