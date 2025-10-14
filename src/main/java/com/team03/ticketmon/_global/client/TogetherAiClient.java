package com.team03.ticketmon._global.client;

import com.team03.ticketmon._global.config.AiServiceProperties;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;
import java.util.Map;

/**
 * 🎯 Together AI API와 HTTP 통신을 담당하는 클래스
 * 팀 예외 처리 규칙을 준수:
 * - BusinessException + ErrorCode 사용
 * - RuntimeException 대신 의미있는 예외 던지기
 * - GlobalExceptionHandler에서 자동 처리됨
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class TogetherAiClient {

	private final AiServiceProperties aiProperties;
	private final RestTemplate restTemplate;

	/**
	 * 🚀 Together AI API로 채팅 요청을 전송하는 메인 메서드
	 *
	 * @param prompt 사용자가 AI에게 보낼 메시지 (리뷰 요약 요청 등)
	 * @return AI가 생성한 응답 텍스트
	 * @throws BusinessException API 호출 실패 시 (팀 규칙 준수)
	 */
	public String sendChatRequest(String prompt) {
		try {
			log.info("Together AI API 요청 시작 - 프롬프트 길이: {}", prompt.length());

			// 1단계: HTTP 요청 객체 생성
			HttpEntity<Map<String, Object>> request = buildHttpRequest(prompt);

			// 2단계: AI 서버에 실제 요청 전송
			ResponseEntity<Map> response = restTemplate.exchange(
				aiProperties.getApiUrl(),
				HttpMethod.POST,
				request,
				Map.class
			);

			// 3단계: AI 응답 처리 및 검증
			return handleApiResponse(response);

		} catch (HttpClientErrorException e) {
			// 우리가 잘못 요청한 경우 (400번대 에러)
			log.error("AI API 클라이언트 오류 - 상태코드: {}, 응답: {}",
				e.getStatusCode(), e.getResponseBodyAsString());

			throw new BusinessException(ErrorCode.AI_REQUEST_INVALID,
				"AI API 요청이 올바르지 않습니다: " + e.getMessage());

		} catch (HttpServerErrorException e) {
			// AI 서버에 문제가 있는 경우 (500번대 에러)
			log.error("AI API 서버 오류 - 상태코드: {}, 응답: {}",
				e.getStatusCode(), e.getResponseBodyAsString());

			throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
				"AI 서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");

		} catch (ResourceAccessException e) {
			// 네트워크 연결 문제인 경우 (타임아웃, 연결 실패 등)
			log.error("AI API 네트워크 오류: {}", e.getMessage());

			throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
				"네트워크 연결에 문제가 발생했습니다. 잠시 후 다시 시도해주세요.");

		} catch (BusinessException e) {
			// 이미 우리가 정의한 비즈니스 예외는 그대로 다시 던지기
			throw e;

		} catch (Exception e) {
			// 예상하지 못한 모든 기타 오류
			log.error("AI API 호출 중 예상치 못한 오류 발생", e);

			// 팀 규칙 준수: BusinessException + ErrorCode 사용
			throw new BusinessException(ErrorCode.SERVER_ERROR,
				"리뷰 요약 처리 중 오류가 발생했습니다.");
		}
	}

	/**
	 * 🔧 HTTP 요청 객체를 생성하는 헬퍼 메서드
	 *
	 * @param prompt 사용자 메시지
	 * @return 완성된 HTTP 요청 객체
	 * @throws BusinessException 요청 생성 실패 시
	 */
	private HttpEntity<Map<String, Object>> buildHttpRequest(String prompt) {
		try {
			// 입력값 검증 (팀 규칙 준수)
			if (prompt == null || prompt.trim().isEmpty()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
					"AI에게 보낼 메시지가 비어있습니다.");
			}

			// HTTP 헤더 설정
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(aiProperties.getApiKey());
			headers.setContentType(MediaType.APPLICATION_JSON);

			// 요청 바디 구성
			Map<String, Object> requestBody = Map.of(
				"model", aiProperties.getModel(),
				"messages", List.of(
					Map.of("role", "system", "content", aiProperties.getSystemPrompt()),
					Map.of("role", "user", "content", prompt.trim())
				),
				"stream", false,
				"temperature", 0.7,              // 창의성 조절
				"repetition_penalty", 1.1,       // 반복 방지 (1.0~1.3 권장)
				"frequency_penalty", 0.1,        // 빈도 기반 반복 방지
				"presence_penalty", 0.1,         // 존재 기반 반복 방지
				"max_tokens", 700              // 토큰 수 제한으로 길이 조절
			);

			return new HttpEntity<>(requestBody, headers);

		} catch (BusinessException e) {
			// 이미 정의된 비즈니스 예외는 그대로 전파
			throw e;
		} catch (Exception e) {
			// 예상치 못한 오류는 서버 에러로 처리
			log.error("HTTP 요청 생성 중 오류 발생", e);
			throw new BusinessException(ErrorCode.SERVER_ERROR,
				"AI API 요청 생성에 실패했습니다.");
		}
	}

	/**
	 * 🔍 AI API 응답을 처리하고 검증하는 메서드
	 *
	 * @param response AI 서버로부터 받은 원본 응답
	 * @return 정리된 AI 답변 텍스트
	 * @throws BusinessException 응답 처리 실패 시
	 */
	private String handleApiResponse(ResponseEntity<Map> response) {
		try {
			// 1단계: HTTP 상태 코드 검증
			if (!response.getStatusCode().is2xxSuccessful()) {
				log.error("AI API 응답 상태 코드 오류: {}", response.getStatusCode());
				throw new BusinessException(ErrorCode.SERVER_ERROR,
					"AI 서비스로부터 올바른 응답을 받지 못했습니다.");
			}

			// 2단계: 응답 본문 존재 여부 확인
			Map<String, Object> responseBody = response.getBody();
			if (responseBody == null) {
				log.error("AI API 응답 본문이 null입니다.");
				throw new BusinessException(ErrorCode.SERVER_ERROR,
					"AI 서비스 응답이 비어있습니다.");
			}

			// 3단계: AI 응답 구조 파싱 및 검증
			// AI 응답 구조: { "choices": [ { "message": { "content": "실제 답변" } } ] }

			List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
			if (choices == null || choices.isEmpty()) {
				log.error("AI API 응답에 choices 배열이 없거나 비어있습니다.");
				throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
					"AI 서비스 응답 형식이 올바르지 않습니다.");
			}

			Map<String, Object> firstChoice = choices.get(0);
			if (firstChoice == null) {
				log.error("AI API 응답의 첫 번째 choice가 null입니다.");
				throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
					"AI 서비스 응답을 파싱할 수 없습니다.");
			}

			Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
			if (message == null) {
				log.error("AI API 응답에 message 객체가 없습니다.");
				throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
					"AI 서비스 응답에서 메시지를 찾을 수 없습니다.");
			}

			String content = (String) message.get("content");
			if (content == null || content.trim().isEmpty()) {
				log.error("AI API 응답의 content가 null이거나 비어있습니다.");
				throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
					"AI가 빈 응답을 반환했습니다. 다시 시도해주세요.");
			}

			log.info("Together AI API 응답 수신 완료 - 응답 길이: {}", content.length());
			return content.trim();

		} catch (BusinessException e) {
			// 이미 정의된 비즈니스 예외는 그대로 전파
			throw e;
		} catch (ClassCastException e) {
			// JSON 구조가 예상과 다른 경우
			log.error("AI API 응답 구조 파싱 오류", e);
			throw new BusinessException(ErrorCode.SERVER_ERROR,
				"AI 서비스 응답 형식을 해석할 수 없습니다.");
		} catch (Exception e) {
			// 기타 예상치 못한 오류
			log.error("AI API 응답 처리 중 예상치 못한 오류", e);
			throw new BusinessException(ErrorCode.SERVER_ERROR,
				"AI 응답 처리 중 오류가 발생했습니다.");
		}
	}
}