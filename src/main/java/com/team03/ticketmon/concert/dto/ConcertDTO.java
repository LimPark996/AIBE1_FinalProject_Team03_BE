package com.team03.ticketmon.concert.dto;

import com.team03.ticketmon.concert.domain.enums.ConcertStatus;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

/**
 * ConcertDTO — 콘서트 응답용 전송 객체 (사용자용 조회 API에서 사용)
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConcertDTO {
	private Long concertId;
	private String title;
	private String artist;
	private String description;
	private Long sellerId;
	private String venueName;
	private String venueAddress;
	private String venueCapacityType;
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
