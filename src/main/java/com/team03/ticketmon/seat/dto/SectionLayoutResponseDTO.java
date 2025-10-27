package com.team03.ticketmon.seat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 구역별 좌석 배치 정보 응답 DTO
 * 특정 구역(A, B, VIP 등)의 좌석 배치 정보를 담는 DTO
 */
@Schema(description = "구역별 좌석 배치 정보")
public record SectionLayoutResponseDTO(

        @Schema(description = "구역명", example = "A")
        String sectionName,

        @Schema(description = "구역 설명", example = "일반석 A구역")
        String sectionDescription,

        @Schema(description = "총 좌석 수", example = "50")
        Integer totalSeats,

        @Schema(description = "예매 가능 좌석 수", example = "45")
        Integer availableSeats,

        @Schema(description = "가격 범위")
        PriceRange priceRange,

        @Schema(description = "좌석 목록")
        List<SeatDetailResponseDTO> seats,

        @Schema(description = "열별 좌석 배치 (프론트엔드 렌더링용)")
        Map<String, List<SeatDetailResponseDTO>> seatsByRow
) {

    /**
     * 가격 범위 정보
     */
    @Schema(description = "가격 범위 정보")
    public record PriceRange(
            @Schema(description = "최저 가격", example = "80000")
            BigDecimal minPrice,

            @Schema(description = "최고 가격", example = "120000")
            BigDecimal maxPrice
    ) {}

    /**
     * 특정 "구역"의 좌석 목록으로부터 SectionLayoutResponse 생성
     * @param sectionName 구역명
     * @param seats 해당 구역의 좌석 목록
     * @return SectionLayoutResponse 객체
     */
    public static SectionLayoutResponseDTO from(String sectionName, List<SeatDetailResponseDTO> seats) {
        if (seats.isEmpty()) { // 특정 구역에서 좌석 목록이 비어있는 경우
            return new SectionLayoutResponseDTO(
                    sectionName,
                    sectionName + "구역",
                    0,
                    0,
                    new PriceRange(BigDecimal.ZERO, BigDecimal.ZERO),
                    seats,
                    Map.of()
            );
        }

        // 특정 구역에서 좌석 목록이 존재하는 경우
        // 가격 범위 계산
        BigDecimal minPrice = seats.stream()
                .map(SeatDetailResponseDTO::price)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPrice = seats.stream()
                .map(SeatDetailResponseDTO::price)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // 예매 가능 좌석 수 계산 (isAvailable())
        int availableSeats = (int) seats.stream()
                .mapToLong(seat -> seat.isAvailable() ? 1 : 0)
                .sum();

        // 열별 좌석 그룹핑 (프론트엔드에서 배치도 렌더링 시 사용)
        // 이 메서드 안에서는 같은 구역의 좌석들만 있기 때문에 열만으로 그룹화함
        Map<String, List<SeatDetailResponseDTO>> seatsByRow = seats.stream()
                .collect(java.util.stream.Collectors.groupingBy(SeatDetailResponseDTO::seatRow));

        return new SectionLayoutResponseDTO(
                sectionName,
                generateSectionDescription(sectionName),
                seats.size(),
                availableSeats,
                new PriceRange(minPrice, maxPrice),
                seats,
                seatsByRow
        );
    }

    /**
     * 구역명에 따른 설명 생성
     * @param sectionName 구역명
     * @return 구역 설명
     */
    private static String generateSectionDescription(String sectionName) {
        return switch (sectionName.toUpperCase()) {
            case "VIP" -> "VIP석";
            case "R" -> "R석";
            case "S" -> "S석";
            case "A" -> "A석";
            case "B" -> "B석";
            case "C" -> "C석";
            default -> sectionName + "구역";
        };
    }
}