package com.team03.ticketmon.seat.dto;

import com.team03.ticketmon.venue.dto.VenueDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 전체 좌석 배치도 응답 DTO
 * 콘서트의 전체 좌석 배치도 정보를 담는 최상위 응답 DTO
 */
@Schema(description = "콘서트 좌석 배치도 전체 정보")
public record SeatLayoutResponseDTO(

        @Schema(description = "콘서트 ID", example = "1")
        Long concertId,

        @Schema(description = "공연장 정보")
        VenueInfo venueInfo,

        @Schema(description = "좌석 통계 정보")
        SeatStatistics statistics,

        @Schema(description = "구역별 좌석 배치")
        List<SectionLayoutResponseDTO> sections,

        @Schema(description = "조회 시간")
        LocalDateTime retrievedAt
) {

    /**
     * 공연장 정보: VenueDTO 에서 공연장 ID 와 공연장명만 추출한다.
     */
    @Schema(description = "공연장 정보")
    public record VenueInfo(
            @Schema(description = "공연장 ID", example = "1")
            Long venueId,

            @Schema(description = "공연장명", example = "올림픽공원 체조경기장")
            String venueName
    ) {
        public static VenueInfo from(VenueDTO venue) {
            return new VenueInfo(venue.getVenueId(), venue.getName());
        }
    }

    /**
     * 좌석 통계 정보
     */
    @Schema(description = "좌석 통계 정보")
    public record SeatStatistics(
            @Schema(description = "총 좌석 수", example = "1000")
            Integer totalSeats,

            @Schema(description = "예매 가능 좌석 수", example = "856")
            Integer availableSeats,

            @Schema(description = "이미 예매된 좌석 수", example = "144")
            Integer bookedSeats,

            @Schema(description = "예매 가능 비율", example = "85.6")
            Double availabilityRate,

            @Schema(description = "가격 범위")
            PriceRange priceRange
    ) {}

    /**
     * 전체 가격 범위 정보
     */
    @Schema(description = "전체 가격 범위 정보")
    public record PriceRange(
            @Schema(description = "최저 가격", example = "50000")
            BigDecimal minPrice,

            @Schema(description = "최고 가격", example = "200000")
            BigDecimal maxPrice
    ) {}

    /**
     * 각 구역별 좌석 정보로부터 전체 좌석 배치도 응답 생성
     * @param concertId 콘서트 ID
     * @param venueInfo 공연장 정보
     * @param sections 구역별 좌석 정보
     * @return SeatLayoutResponse 객체
     */
    public static SeatLayoutResponseDTO from(Long concertId, VenueInfo venueInfo, List<SectionLayoutResponseDTO> sections) {
        // 전체 좌석 수 계산
        int totalSeats = sections.stream()
                .mapToInt(SectionLayoutResponseDTO::totalSeats)
                .sum();

        // 전체 예매 가능 좌석 수 계산
        int availableSeats = sections.stream()
                .mapToInt(SectionLayoutResponseDTO::availableSeats)
                .sum();

        // 전체 예매가 이미 되어있는 좌석 수 계산
        int bookedSeats = totalSeats - availableSeats;

        double availabilityRate = totalSeats > 0 ?
                (double) availableSeats / totalSeats * 100 : 0.0;

        // 전체 가격 범위 계산
        BigDecimal minPrice = sections.stream()
                .map(section -> section.priceRange().minPrice())
                .filter(price -> price.compareTo(BigDecimal.ZERO) > 0)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPrice = sections.stream()
                .map(section -> section.priceRange().maxPrice())
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        SeatStatistics statistics = new SeatStatistics(
                totalSeats,
                availableSeats,
                bookedSeats,
                Math.round(availabilityRate * 10.0) / 10.0, // 소수점 첫째자리까지
                new PriceRange(minPrice, maxPrice)
        );

        return new SeatLayoutResponseDTO(
                concertId,
                venueInfo,
                statistics,
                sections,
                LocalDateTime.now()
        );
    }
}