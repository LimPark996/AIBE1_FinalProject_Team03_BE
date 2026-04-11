package com.team03.ticketmon.payment.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.service.PaymentService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebhookController — 토스페이먼츠 웹훅 수신 엔드포인트
 *
 * 이 클래스가 하는 일:
 *   1. 토스페이먼츠에서 보내는 웹훅 페이로드 수신 및 JSON 파싱
 *   2. HMAC-SHA256 기반 서명 검증 (PAYMENT_STATUS_CHANGED는 서명 미발급이라 예외)
 *   3. PAYMENT_STATUS_CHANGED 이벤트에 한해 PaymentService로 상태 동기화 위임
 *
 * 동작 흐름:
 *   - 토스 서버 → POST /api/v1/webhooks/toss/payment-updates
 *   - 서명 검증(필요 시) → orderId/status 추출 → PaymentService.updatePaymentStatusByWebhook
 *   - 예외가 나도 토스가 재전송하지 않도록 파싱 오류는 2xx로 응답
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/toss")
@RequiredArgsConstructor
public class WebhookController {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final PaymentService paymentService;
	private final TossPaymentsProperties tossPaymentsProperties;

	/**
	 * 토스페이먼츠 웹훅 수신 API
	 * - 서명이 있는 웹훅은 검증하고, 없는 웹훅은 예외 처리합니다.
	 */
	@PostMapping("/payment-updates")
	public ResponseEntity<String> handleTossPaymentWebhook(HttpServletRequest request) { // 💡 HttpServletRequest를 직접 받음
		try {
			// 1. 원본 요청 바디를 읽음
			String requestBody = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
			JsonNode jsonNode = objectMapper.readTree(requestBody);
			String eventType = jsonNode.get("eventType").asText();

			// "PAYMENT_STATUS_CHANGED" 이벤트가 아닌 경우에만 서명을 검증합니다.
			if (!"PAYMENT_STATUS_CHANGED".equals(eventType)) {
				verifySignature(request, requestBody);
				log.info("토스페이먼츠 웹훅 서명 검증 성공 (eventType: {})", eventType);
			} else {
				log.info("PAYMENT_STATUS_CHANGED 이벤트이므로 서명 검증을 건너뜁니다.");
			}

			// 3. 서명 검증 성공 또는 예외 처리 후, 비즈니스 로직 실행
			if ("PAYMENT_STATUS_CHANGED".equals(eventType)) {
				JsonNode data = jsonNode.get("data");
				String orderId = data.get("orderId").asText();
				String status = data.get("status").asText();
				paymentService.updatePaymentStatusByWebhook(orderId, status);
			}

			return ResponseEntity.ok("Webhook processed successfully.");
		} catch (SecurityException e) {
			log.warn("웹훅 서명 검증 실패: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
		} catch (IOException e) {
			log.error("웹훅 페이로드 파싱 실패: {}", e.getMessage(), e);
			// 파싱 실패 시에도 토스 서버에는 2xx 응답을 보내야 재전송을 막을 수 있습니다.
			return ResponseEntity.ok("Webhook payload parsing error, but acknowledged.");
		} catch (Exception e) {
			log.error("웹훅 처리 중 알 수 없는 오류 발생: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error.");
		}
	}

	/**
	 * 토스페이먼츠 웹훅 서명을 검증하는 private 헬퍼 메서드
	 * @param request HttpServletRequest 객체
	 * @param payload 웹훅 요청의 원본 바디 (JSON 문자열)
	 * @throws SecurityException 서명 검증 실패 시
	 */
	private void verifySignature(HttpServletRequest request, String payload) throws SecurityException {
		String signature = request.getHeader("tosspayments-webhook-signature");
		String transmissionTime = request.getHeader("tosspayments-webhook-transmission-time");

		if (signature == null || transmissionTime == null) {
			throw new SecurityException("웹훅 서명 또는 시간 헤더가 누락되었습니다.");
		}

		String dataToSign = payload + ":" + transmissionTime;
		String secretKey = tossPaymentsProperties.secretKey();

		try {
			Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secret_key_spec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			sha256_HMAC.init(secret_key_spec);

			byte[] hash = sha256_HMAC.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
			String calculatedSignature = Base64.getEncoder().encodeToString(hash);

			// 타이밍 공격에 안전한 비교를 위해 MessageDigest.isEqual 사용
			if (!MessageDigest.isEqual(calculatedSignature.getBytes(StandardCharsets.UTF_8),
				signature.getBytes(StandardCharsets.UTF_8))) {
				throw new SecurityException("계산된 서명이 헤더의 서명과 일치하지 않습니다.");
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			log.error("웹훅 서명 검증 중 암호화 오류 발생", e);
			throw new SecurityException("서명 검증 과정에서 내부 오류가 발생했습니다.", e);
		}
	}
}
