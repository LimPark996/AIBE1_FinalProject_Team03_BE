package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.venue.domain.Seat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 개별 좌석 정보 응답 DTO
 * 좌석 배치도에서 각 좌석의 상세 정보를 담는 DTO
 */
@Schema(description = "개별 좌석 정보")
public record SeatDetailResponseDTO(

        @Schema(description = "좌석 ID", example = "1")
        Long seatId,

        @Schema(description = "구역", example = "A")
        String section,

        @Schema(description = "열", example = "1")
        String seatRow,

        @Schema(description = "번호", example = "1")
        Integer seatNumber,

        @Schema(description = "좌석 표시명", example = "A-1-1")
        String seatLabel,

        @Schema(description = "좌석 등급", example = "VIP")
        com.team03.ticketmon.concert.domain.enums.SeatGrade grade,

        @Schema(description = "가격", example = "150000")
        BigDecimal price,

        @Schema(description = "예매 가능 여부", example = "true")
        Boolean isAvailable
) {

    /**
     * ConcertSeat 엔티티로부터 SeatDetailResponse 생성
     * @param concertSeat 콘서트 좌석 정보
     * @return SeatDetailResponse 객체
     */
    public static SeatDetailResponseDTO from(ConcertSeat concertSeat) {
        Seat seat = concertSeat.getSeat();

        // 좌석 표시명 생성 (구역-열-번호 형식)
        String seatLabel = String.format("%s-%s-%d",
                seat.getSection(),
                seat.getSeatRow(),
                seat.getSeatNumber());

        // 예매 가능 여부 판단 (티켓이 없으면 예매 가능)
        boolean isAvailable = concertSeat.getTicket() == null;

        return new SeatDetailResponseDTO(
                seat.getSeatId(),
                seat.getSection(),
                seat.getSeatRow(),
                seat.getSeatNumber(),
                seatLabel,
                concertSeat.getGrade(),
                concertSeat.getPrice(),
                isAvailable
        );
    }
}