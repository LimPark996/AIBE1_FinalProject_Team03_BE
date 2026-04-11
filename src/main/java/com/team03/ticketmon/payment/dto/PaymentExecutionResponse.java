package com.team03.ticketmon.payment.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

/**
 * PaymentExecutionResponse — 프론트가 토스페이먼츠 결제창을 호출할 때 필요한 정보 묶음.
 * orderId/금액/clientKey/성공·실패 리다이렉트 URL을 포함한다.
 */
@Getter
@Builder
public class PaymentExecutionResponse {
	private String orderId;
	private String bookingNumber;
	private String orderName;
	private BigDecimal amount;
	private String customerName; // 예시 필드
	private String clientKey;
	private String successUrl;
	private String failUrl;
}
