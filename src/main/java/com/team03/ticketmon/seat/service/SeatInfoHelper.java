package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.venue.domain.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 좌석 정보 헬퍼 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatInfoHelper {

    private final ConcertSeatRepository concertSeatRepository;

    /**
     * ConcertSeat ID 기반 좌석 정보 "하나" 조회
     * 컨트롤러에서 ConcertSeat ID를 사용할 때 호출
     *
     * @param concertId 콘서트 ID
     * @param concertSeatId ConcertSeat ID
     * @return 좌석 정보 문자열 (예: "A-1-15" = 구역-열-번호)
     * @throws BusinessException 좌석을 찾을 수 없는 경우
     */
    @Cacheable(value = "seatInfoByConcertSeatId", key = "#concertId + ':' + #concertSeatId", unless = "#result == null")
    public String getSeatInfoByConcertSeatId(Long concertId, Long concertSeatId) {
        try {
            log.debug("ConcertSeat ID로 좌석 정보 조회: concertId={}, concertSeatId={}", concertId, concertSeatId);

            // ConcertSeat ID로 직접 조회
            Optional<ConcertSeat> concertSeatOpt = concertSeatRepository.findById(concertSeatId);

            if (concertSeatOpt.isEmpty()) {
                log.warn("ConcertSeat을 찾을 수 없음: concertSeatId={}", concertSeatId);
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                        String.format("ConcertSeat을 찾을 수 없습니다. ConcertSeat ID: %d", concertSeatId));
            }

            // ConcertSeat이 Id로 조회가 된다면,
            ConcertSeat concertSeat = concertSeatOpt.get();

            // ConcertSeat 에서 호출한 Concert의 Id(expected)와 Concert 에서 호출한 Id(actual)가 동일한지 확인
            if (!concertSeat.getConcert().getConcertId().equals(concertId)) {
                log.warn("콘서트 ID 불일치: expected={}, actual={}, concertSeatId={}",
                        concertId, concertSeat.getConcert().getConcertId(), concertSeatId);
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                        "해당 콘서트에 속하지 않는 좌석입니다."); // 동일하지 않는 경우 Exception 반환
            }

            Seat seat = concertSeat.getSeat();

            // 좌석 정보 포맷: 구역-열-번호 (A-1-15)
            String seatInfo = String.format("%s-%s-%d",
                    seat.getSection() != null ? seat.getSection() : "?",
                    seat.getSeatRow() != null ? seat.getSeatRow() : "?",
                    seat.getSeatNumber() != null ? seat.getSeatNumber() : 0);

            log.debug("ConcertSeat ID 기반 좌석 정보 조회 성공: concertSeatId={}, seatInfo={}",
                    concertSeatId, seatInfo);

            return seatInfo; // 좌석 정보 포맷: 구역-열-번호 반환

        } catch (BusinessException e) { // 비즈니스 예외는 그대로 상위로 전달 (메서드 호출 체인을 따라 호출한 쪽으로 예외를 던짐)
            throw e;
        } catch (Exception e) { // 예상 못한 기술적 오류는 비즈니스 예외로 변환
            log.error("ConcertSeat ID 기반 좌석 정보 조회 중 오류: concertSeatId={}", concertSeatId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "좌석 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 더미 데이터 생성
     */
    public String generateDummySeatInfo(int seatNumber) {
        log.debug("더미 좌석 정보 생성: seatNumber={}", seatNumber);

        // 좌석 번호 유효성 검증 (1 ~ 150 까지 번호가 유효함)
        if (seatNumber < 1 || seatNumber > 150) {
            log.error("유효하지 않은 좌석 번호: seatNumber={}", seatNumber);
            throw new IllegalArgumentException(
                    String.format("좌석 번호는 1부터 150 사이여야 합니다. 입력된 값: %d", seatNumber));
        }

        final int SEATS_PER_SECTION = 50;
        String section;
        int seatInSection;

        if (seatNumber <= SEATS_PER_SECTION) {
            section = "A";
            seatInSection = seatNumber;
        } else if (seatNumber <= SEATS_PER_SECTION * 2) {
            section = "B";
            seatInSection = seatNumber - SEATS_PER_SECTION;
        } else {
            section = "C";
            seatInSection = seatNumber - (SEATS_PER_SECTION * 2);
        }

        String seatInfo = String.format("%s-%d", section, seatInSection);
        log.debug("더미 좌석 정보 생성 완료: seatNumber={} -> seatInfo={}", seatNumber, seatInfo);

        return seatInfo; // 더미 좌석 정보 생성
    }
}