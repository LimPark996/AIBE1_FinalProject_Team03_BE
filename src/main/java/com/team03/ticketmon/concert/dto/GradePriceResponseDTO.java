package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import java.math.BigDecimal;

public record GradePriceResponseDTO(
        String grade,
        String gradeName,
        BigDecimal price,
        Integer totalSeats,      
        Integer availableSeats   
) {
    // 기존 메서드 (하위 호환성 유지)
    public static GradePriceResponseDTO from(SeatGrade grade, BigDecimal price) {
        return new GradePriceResponseDTO(
                grade.name(),
                getGradeName(grade),
                price,
                null,
                null
        );
    }

    // 새 메서드 (카운트 포함)
    public static GradePriceResponseDTO from(SeatGrade grade, BigDecimal price, int totalSeats, int availableSeats) {
        return new GradePriceResponseDTO(
                grade.name(),
                getGradeName(grade),
                price,
                totalSeats,
                availableSeats
        );
    }

    private static String getGradeName(SeatGrade grade) {
        return switch (grade) {
            case VIP -> "VIP석";
            case R -> "R석";
            case S -> "S석";
            case A -> "A석";
        };
    }
}