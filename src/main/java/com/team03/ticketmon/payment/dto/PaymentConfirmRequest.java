package com.team03.ticketmon.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * PaymentConfirmRequest — 토스페이먼츠 결제 승인 API 호출용 요청 DTO.
 * paymentKey/orderId/amount는 토스가 필수로 요구하는 값이다.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentConfirmRequest {
    @NotBlank
    private String paymentKey;
    @NotBlank
    private String orderId;
    @NotNull
    private BigDecimal amount;
    @NotBlank
    private String originalMethod;  // "카드" 혹은 "간편결제"
}
