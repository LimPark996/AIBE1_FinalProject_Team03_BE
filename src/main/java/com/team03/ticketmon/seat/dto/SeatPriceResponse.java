package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.concert.domain.ConcertSeat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 개별 좌석(ConcertSeat)의 가격 정보를 담는 응답 DTO. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SeatPriceResponse {
    private Long concertSeatId;
    private String seatInfo;  // 좌석 정보 (예: "A-3-12")
    private BigDecimal price;

    public static SeatPriceResponse from(ConcertSeat concertSeat) {
        return new SeatPriceResponse(
                concertSeat.getConcertSeatId(),
                concertSeat.getSeatInfo(),
                concertSeat.getPrice()
        );
    }
}