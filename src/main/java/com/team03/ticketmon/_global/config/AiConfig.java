package com.team03.ticketmon._global.config;

// @Value: yml 설정 파일에서 값을 하나씩 읽어와서 변수에 넣어주는 어노테이션
// → S3Config에서 accessKey, secretKey 읽어올 때 썼던 그것!
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// Spring AI 라이브러리에서 제공하는 ChatClient 클래스
// → AI 모델(예: OpenAI의 GPT)과 대화(채팅)할 수 있게 해주는 도구
// → 이 객체를 통해 AI에게 질문을 보내고, AI의 답변을 받을 수 있음
// → S3Config의 S3Client가 "S3 접속 도구"였다면,
//   ChatClient는 "AI 접속 도구"라고 생각하면 됨
import org.springframework.ai.chat.client.ChatClient;

@Configuration

// → AI 관련 설정을 담당하는 클래스
// → ChatClient(AI 대화 도구)를 만들어서 스프링에게 맡기는 역할
public class AiConfig {

    // @Value("${spring.ai.summary.system-prompt}")
    // → yml 파일에서 spring.ai.summary.system-prompt 값을 읽어와서 변수에 넣어줌
    // 왜 yml에 넣을까?
    // → 코드를 수정하지 않고도 yml 파일만 바꾸면 AI의 동작 방식을 변경할 수 있음
    // → 예: "200자 요약" → "500자 요약"으로 바꾸고 싶으면 yml만 수정하면 됨
    @Value("${spring.ai.summary.system-prompt}")
    private String systemPrompt;

    // 1. 이 메서드가 반환하는 ChatClient 객체를 스프링이 관리해줌
    //    → 다른 클래스에서 ChatClient가 필요하면 스프링이 자동으로 넣어줌
    // 2. 파라미터로 ChatClient.Builder builder를 받고 있음!
    //   → 이 builder는 어디서 오는 걸까?
    //   → Spring AI 라이브러리가 자동으로 만들어서 스프링에 등록해둠
    //   → 스프링이 이 메서드를 실행할 때 자동으로 builder를 넣어줌 (의존성 주입)
    //   → 즉, 개발자가 builder를 직접 만들 필요가 없음!
    // 3. S3Config와의 차이:
    //   S3Config: S3Client.builder()로 빌더를 직접 생성함
    //   AiConfig: 스프링이 미리 만들어준 builder를 파라미터로 받아서 사용함
    // ──────────────────────────────────────────────────────────
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(systemPrompt)
                .build();
    }
}