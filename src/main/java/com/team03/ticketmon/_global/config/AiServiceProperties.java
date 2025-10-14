package com.team03.ticketmon._global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import lombok.Data;

@Data
@ConfigurationProperties(prefix = "ai.together")
@Primary
public class AiServiceProperties {

	private String apiKey;
	private String apiUrl = "https://api.together.xyz/v1/chat/completions";
	private String model = "meta-llama/Llama-3.3-70B-Instruct-Turbo";
	private Integer timeoutSeconds = 30;
	private Integer maxRetries = 3;
	private Integer maxTokensPerRequest = 4000;     // 요청당 최대 토큰 수
	private Integer maxReviewsPerRequest = 50;        // 요청당 최대 리뷰 수
	private Double charsPerToken = 2.5;               // 문자당 토큰 추정치
	private Double tokenSafetyMargin = 0.2;           // 안전 마진 (20%)

	// 영어 시스템 프롬프트 + 마크다운 형식 출력
	private String systemPrompt = """
    You are a concert review summarizer. Write in Korean using markdown format.
    
    Structure:
    ### 전체 평가: (3-4 sentences)
    ## 좋은 점: (3-4 sentences)
    ## 아쉬운 점: (3-4 sentences)
    
    Keep it concise and practical.
    """;
}