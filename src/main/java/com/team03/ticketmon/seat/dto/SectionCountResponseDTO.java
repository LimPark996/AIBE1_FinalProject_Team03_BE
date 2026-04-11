package com.team03.ticketmon.seat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 구역별 총/가용 좌석 수 응답 DTO. */
@Schema(description = "구역별 좌석 카운트 정보")
public record SectionCountResponseDTO(
        @Schema(description = "구역명", example = "A")
        String section,

        @Schema(description = "총 좌석 수", example = "50")
        Long totalSeats,

        @Schema(description = "예매 가능 좌석 수", example = "15")
        Long availableSeats
) {}