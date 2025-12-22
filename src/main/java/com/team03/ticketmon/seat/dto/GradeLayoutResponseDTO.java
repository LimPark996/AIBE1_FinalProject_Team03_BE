package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.concert.domain.enums.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 등급별 좌석 배치 정보 응답 DTO
 * 특정 등급(A, B, VIP 등)의 좌석 배치 정보를 담는 DTO
 */
@Schema(description = "등급별 좌석 배치 정보")
public record GradeLayoutResponseDTO(

        @Schema(description = "등급명", example = "A")
        String gradeName,

        @Schema(description = "등급 설명", example = "일반석 A구역")
        String gradeDescription,

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
     * 특정 "등급"의 좌석 목록으로부터 GradeLayoutResponse 생성
     * @param grade 등급명
     * @param seats 해당 등급의 좌석 목록
     * @return GradeLayoutResponse 객체
     */
    public static GradeLayoutResponseDTO from(SeatGrade grade, List<SeatDetailResponseDTO> seats) {
        String gradeName = grade.name();  // "VIP", "R", "S", "A"

        if (seats.isEmpty()) {
            return new GradeLayoutResponseDTO(
                    gradeName,
                    generateGradeDescription(gradeName),
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
                .filter(SeatDetailResponseDTO::isAvailable)
                .count();

        // 열별 좌석 그룹핑 (프론트엔드에서 배치도 렌더링 시 사용)
        // 이 메서드 안에서는 같은 그룹의 좌석들만 있기 때문에 열만으로 그룹화함
        Map<String, List<SeatDetailResponseDTO>> seatsByRow = seats.stream()
                .collect(Collectors.groupingBy(SeatDetailResponseDTO::seatRow));

        return new GradeLayoutResponseDTO(
                gradeName,
                generateGradeDescription(gradeName),
                seats.size(),
                availableSeats,
                new PriceRange(minPrice, maxPrice),
                seats,
                seatsByRow
        );
    }

    /**
     * 등급명에 따른 설명 생성
     * @param gradeName 등급명
     * @return 등급 설명
     */
    private static String generateGradeDescription(String gradeName) {
        return switch (gradeName.toUpperCase()) {
            case "VIP" -> "VIP석";
            case "R" -> "R석";
            case "S" -> "S석";
            case "A" -> "A석";
            case "B" -> "B석";
            case "C" -> "C석";
            default -> gradeName + "석";
        };
    }
}