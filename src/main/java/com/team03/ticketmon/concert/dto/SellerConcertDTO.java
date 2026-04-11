package com.team03.ticketmon.concert.dto;

import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * SellerConcertDTO — 판매자 콘서트 응답용 전송 객체
 * 콘서트 기본 정보와 판매자 관점 필드(AI 요약, 포스터 URL 등)를 포함한다.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerConcertDTO {
	private Long concertId;
	private String title;
	private String artist;
	private String description;
	private Long sellerId;
	private String venueName;
	private String venueAddress;
	private LocalDate concertDate;
	private LocalTime startTime;
	private LocalTime endTime;
	private Integer totalSeats;
	private LocalDateTime bookingStartDate;
	private LocalDateTime bookingEndDate;
	private Integer minAge;
	private Integer maxTicketsPerUser;
	private ConcertStatus status;
	private String posterImageUrl;
	private String aiSummary;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
