package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import java.math.BigDecimal;

public record GradePriceResponseDTO(
        String grade,
        String gradeName,
        BigDecimal price
) {
    public static GradePriceResponseDTO from(SeatGrade grade, BigDecimal price) {
        String gradeName = switch (grade) {
            case VIP -> "VIP석";
            case R -> "R석";
            case S -> "S석";
            case A -> "A석";
            default -> grade.name() + "석";
        };
        return new GradePriceResponseDTO(grade.name(), gradeName, price);
    }
}