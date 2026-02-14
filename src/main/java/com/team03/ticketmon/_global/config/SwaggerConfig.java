package com.team03.ticketmon._global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(API 문서) 설정 클래스
 *
 * [ 쉬운 설명 ]
 * Swagger란?
 * → 백엔드 API를 웹 브라우저에서 직접 테스트할 수 있는 "대화형 API 문서"
 * → 프론트엔드 개발자가 "이 API는 어떤 데이터를 보내야 하고, 어떤 응답이 오는지"
 *   쉽게 확인하고 테스트할 수 있음
 *
 * 이 클래스의 역할:
 * 1) API 문서의 제목, 설명, 버전 등 기본 정보 설정
 * 2) JWT 인증을 Swagger에서 테스트할 수 있도록 "Authorize" 버튼 설정
 * 3) API를 그룹별로 분류해서 보기 좋게 정리
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "Authorization";

        return new OpenAPI()
                .info(new Info()
                        .title("Ticketing API")
                        .description("콘서트 예매 시스템 API 명세서")
                        .version("v1.0"))

                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName) // 헤더 이름: Authorization
                                        .type(SecurityScheme.Type.HTTP) // HTTP 헤더 기반 인증
                                        .scheme("bearer")         // Authorization: Bearer {token}
                                        .bearerFormat("JWT")));         // 형식: JWT
    }

    @Bean
    public GroupedOpenApi devApi() {
            return GroupedOpenApi.builder()
                    .group("1. 기능 구현 API 모음")
                    .pathsToMatch("/api/**")
                    .build();
    }

    @Bean
    public GroupedOpenApi redisTestApi() {
        return GroupedOpenApi.builder()
                .group("2. Redis 테스트 API 모음")
                .pathsToMatch("/test/redis/**")
                .build();
    }

    @Bean
    public GroupedOpenApi healthApi() {
        return GroupedOpenApi.builder()
                .group("3. 헬스체크(Redis, ..) API")
                .pathsToMatch("/health/**")
                .build();
    }

    @Bean
    public GroupedOpenApi initTestApi() {
            return GroupedOpenApi.builder()
                    .group("4. 초기 테스트 API 모음")
                    .pathsToMatch("/test/**")
                    .pathsToExclude("/test/redis/**")
                    .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("5. 전체 API(초기 테스트, 헬스체크 포함)")
                .pathsToMatch("/**")
                .pathsToExclude("/example/**")
                .build();
    }

}