package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * ConcertSearchDTO — 콘서트 키워드 검색 요청 DTO
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConcertSearchDTO {

	@NotBlank(message = "검색 키워드는 필수입니다")
	@Size(min = 1, max = 100, message = "검색 키워드는 1자 이상 100자 이하여야 합니다")
	private String keyword;
}
