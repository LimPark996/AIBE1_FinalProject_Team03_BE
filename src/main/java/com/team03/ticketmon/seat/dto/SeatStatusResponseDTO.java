package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;

import java.time.LocalDateTime;

/**
 * 좌석 상태 응답 DTO
 */
public record SeatStatusResponseDTO(
        Long concertId,
        Long seatId,
        String seatInfo,
        SeatStatusEnum status,
        String grade,
        java.math.BigDecimal price,
        String seatRow,
        Integer seatNumber,
        boolean isReservedByCurrentUser,
        LocalDateTime reservedAt,
        LocalDateTime expiresAt,
        Long remainingSeconds
) {

    /**
     * SeatStatus 엔티티로부터 응답 DTO 생성
     */
    public static SeatStatusResponseDTO from(SeatStatus seatStatus, Long currentUserId) {
        Long remainingSeconds = null;
        if (seatStatus.isReserved() && seatStatus.getExpiresAt() != null) {
            remainingSeconds = java.time.Duration.between(
                    LocalDateTime.now(),
                    seatStatus.getExpiresAt()
            ).getSeconds();
            remainingSeconds = Math.max(0, remainingSeconds); // 음수 방지
        }
        boolean isMine = seatStatus.isReserved() && currentUserId != null && currentUserId.equals(seatStatus.getUserId());


        return new SeatStatusResponseDTO(
                seatStatus.getConcertId(),
                seatStatus.getSeatId(),
                seatStatus.getSeatInfo(),
                seatStatus.getStatus(),
                seatStatus.getGrade(),
                seatStatus.getPrice(),
                seatStatus.getSeatRow(),
                seatStatus.getSeatNumber(),
                isMine,
                seatStatus.getReservedAt(),
                seatStatus.getExpiresAt(),
                remainingSeconds
        );
    }
}