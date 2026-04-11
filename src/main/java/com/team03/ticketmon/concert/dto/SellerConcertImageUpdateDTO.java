package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * SellerConcertImageUpdateDTO — 콘서트 포스터 이미지 URL 부분 업데이트 요청 DTO
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerConcertImageUpdateDTO {

	@NotBlank(message = "포스터 이미지 URL은 필수입니다")
	@Size(max = 2000, message = "URL이 너무 깁니다")
	private String posterImageUrl;
}
