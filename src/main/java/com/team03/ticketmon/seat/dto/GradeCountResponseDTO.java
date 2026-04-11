package com.team03.ticketmon.seat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 등급별 총/가용 좌석 수 응답 DTO. */
@Schema(description = "등급별 좌석 카운트 정보")
public record GradeCountResponseDTO(
        @Schema(description = "등급 코드", example = "VIP")
        String grade,

        @Schema(description = "총 좌석 수", example = "100")
        Long totalSeats,

        @Schema(description = "예매 가능 좌석 수", example = "45")
        Long availableSeats
) {}