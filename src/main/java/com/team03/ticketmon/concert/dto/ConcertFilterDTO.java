package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Concert Filter DTO
 * 콘서트 필터 조건 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConcertFilterDTO {

	private LocalDate startDate;

	private LocalDate endDate;

	@DecimalMin(value = "0", message = "최소 가격은 0원 이상이어야 합니다")
	@Digits(integer = 10, fraction = 2, message = "가격은 올바른 형식이어야 합니다")
	private BigDecimal priceMin;

	@DecimalMin(value = "0", message = "최대 가격은 0원 이상이어야 합니다")
	@Digits(integer = 10, fraction = 2, message = "가격은 올바른 형식이어야 합니다")
	private BigDecimal priceMax;

}