package com.team03.ticketmon._global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
// → 이걸 붙여야 @NotBlank 같은 검사 어노테이션이 실제로 동작함
// → @Validated 없이 @NotBlank만 붙이면 검사가 안 됨! (스위치를 안 켠 것과 같음)
import org.springframework.validation.annotation.Validated;

@Validated
// → yml 파일에서 "app" 아래의 값들을 이 클래스에 자동 매핑
@ConfigurationProperties(prefix = "app")

// record란? (Java 16+에서 추가된 문법)
// → "데이터만 담는 클래스"를 아주 간결하게 만드는 방법
// → class로 만들면 필드, 생성자, getter, equals, hashCode, toString을
//   전부 직접 쓰거나 Lombok(@Data)을 붙여야 했는데,
//   record를 쓰면 이 모든 것이 "자동 생성"됨!
//
// 아래 한 줄이 이 모든 코드와 같은 효과:
//   private final String baseUrl;
//   private final String frontBaseUrl;
//   public AppProperties(String baseUrl, String frontBaseUrl) { ... }
//   public String baseUrl() { return baseUrl; }     ← getter (getBaseUrl이 아님!)
//   public String frontBaseUrl() { return frontBaseUrl; }
//   public boolean equals(...) { ... }
//   public int hashCode() { ... }
//   public String toString() { ... }
//
// record는 Setter가 없음!
//   → 그런데 @ConfigurationProperties는 어떻게 값을 넣지?
//   → record의 경우 "생성자 바인딩"을 사용함
//   → yml 값을 생성자 파라미터로 넣어서 객체를 만듦 (Setter 불필요)
public record AppProperties(

        // String baseUrl: 백엔드 API 서버의 기본 URL
        @NotBlank String baseUrl,

        // String frontBaseUrl: 프론트엔드(사용자 화면)의 기본 URL
        // → 결제 완료 후 리다이렉트, 이메일 링크 등에서 사용
        // → 백엔드와 프론트엔드 URL이 다를 수 있으므로 따로 관리
        @NotBlank String frontBaseUrl
) {
}