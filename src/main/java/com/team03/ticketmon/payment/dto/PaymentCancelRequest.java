package com.team03.ticketmon.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PaymentCancelRequest — 결제 취소 요청 DTO. 취소 사유를 토스페이먼츠 취소 API에 그대로 전달한다.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCancelRequest {
	@NotBlank(message = "취소 사유는 필수입니다.")
	private String cancelReason;
}
