package com.team03.ticketmon.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TossPaymentsProperties — application.yml의 toss.* 프로퍼티 바인딩.
 * 토스페이먼츠 API 호출에 쓰는 clientKey/secretKey를 보관한다.
 */
@Validated
@ConfigurationProperties(prefix = "toss")
public record TossPaymentsProperties(
	@NotBlank String clientKey,
	@NotBlank String secretKey
) {}