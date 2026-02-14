package com.team03.ticketmon._global.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 설정 프로퍼티 클래스
 *
 * [ 쉬운 설명 ]
 * CORS(Cross-Origin Resource Sharing)란?
 * → 브라우저에서 "다른 주소(origin)"의 서버에 요청을 보낼 수 있게 허용하는 정책
 *
 * 예: 프론트엔드가 http://localhost:3000 에서 실행 중이고,
 *     백엔드가 http://localhost:8080 에서 실행 중이면
 *     → 주소(origin)가 다르므로 브라우저가 요청을 차단함!
 *     → CORS 설정으로 "localhost:3000은 허용해줘"라고 알려줘야 함
 *
 * 이 클래스는 "어떤 주소(origin)를 허용할지" 목록을 yml에서 읽어와 보관하는 역할
 *
 * yml 설정 예시:
 *   cors:
 *     allowed-origins:
 *       - http://localhost:3000       ← 개발용 프론트엔드
 *       - https://ticketmon.com       ← 운영용 프론트엔드
 */

@Data
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {
    private String[] allowedOrigins;
}