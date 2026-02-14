package com.team03.ticketmon._global.config;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.*;
import com.team03.ticketmon.auth.oauth2.OAuth2LoginFailureHandler;
import com.team03.ticketmon.auth.oauth2.OAuth2LoginSuccessHandler;
import com.team03.ticketmon.auth.service.CustomOAuth2UserService;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import com.team03.ticketmon.auth.service.ReissueService;
import com.team03.ticketmon.queue.adapter.QueueRedisAdapter;
import com.team03.ticketmon.user.service.SocialUserService;
import com.team03.ticketmon.user.service.UserEntityService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Spring Security 설정 클래스
 *
 * [ 쉬운 설명 ]
 * 이 클래스는 "누가 어디에 접근할 수 있는지"를 정하는 보안 관리자 역할
 *
 * 비유: 건물의 출입 관리 시스템
 * → 1층 로비(로그인/회원가입): 누구나 출입 가능
 * → 사무실(일반 API): 사원증(JWT 토큰)이 있어야 출입 가능
 * → 관리자실(admin): 관리자 사원증만 출입 가능
 * → 판매자실(seller): 판매자 사원증만 출입 가능
 *
 * 주요 기능:
 * 1) JWT 기반 인증: 로그인하면 토큰(팔찌)을 발급, 이후 요청마다 토큰으로 확인
 * 2) OAuth2 소셜 로그인: 카카오, 구글 등으로 간편 로그인
 * 3) CORS 설정: 프론트엔드에서 백엔드 API 호출 허용
 * 4) URL별 접근 권한 설정: 공개/인증필요/관리자전용 등 구분
 */
@Configuration
@EnableWebSecurity  // Spring Security 활성화
@EnableMethodSecurity(  // 메서드 수준 보안 (예: @PreAuthorize) 활성화
        securedEnabled = true,  // @Secured 어노테이션 활성화
        prePostEnabled = true,  // @PreAuthorize, @PostAuthorize 어노테이션 활성화
        jsr250Enabled = true    // @RolesAllowed 어노테이션 활성화
)
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final JwtTokenProvider jwtTokenProvider;
    private final ReissueService reissueService;
    private final RefreshTokenService refreshTokenService;
    private final UserEntityService userEntityService;
    private final SocialUserService socialUserService;
    private final CookieUtil cookieUtil;
    private final CorsProperties corsProperties;
    private final QueueRedisAdapter queueRedisAdapter;
    private final AppProperties appProperties;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll() // 인증(로그인, 회원가입) 관련 API 경로 허용 (인증 불필요)
                                .requestMatchers("/auth/**", "/api/auth/me", "/api/auth/register/social").permitAll() // login.html, register.html 등
                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll() // Swagger UI 및 API 문서
                                .requestMatchers(HttpMethod.GET, "/api/concerts", "/api/concerts/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/concerts/{id}/**").permitAll() // 상세 조회, AI 요약
                                .requestMatchers(HttpMethod.GET, "/api/concerts/{id}/reviews").permitAll() // 리뷰 목록 조회
                                .requestMatchers(HttpMethod.GET, "/api/concerts/{id}/expectations").permitAll() // 기대평 목록 조회
                                .requestMatchers("/api/seats/*/cache").permitAll()
                                .requestMatchers("/api/seats/cache").permitAll()
                                .requestMatchers("/api/v1/payments/success", "/api/v1/payments/fail").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/toss/payment-updates").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/seats/concerts/*/polling").permitAll()
                                .requestMatchers("/").permitAll()
                                .requestMatchers("/healthz").permitAll()

                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/api/admin/seats/**").hasRole("ADMIN")

                                .requestMatchers("/api/seller/concerts/**").hasRole("SELLER")

                                .requestMatchers("/api/v1/payments/history").authenticated() // 결제 내역 조회
                                .requestMatchers(HttpMethod.POST, "/api/bookings").authenticated() // 예매 생성 및 결제 준비
                                .requestMatchers(HttpMethod.POST, "/api/bookings/*/cancel").authenticated() // 예매 취소
                                .requestMatchers("/api/users/me/seller-status").authenticated() // 판매자 권한 UI 접근 시 로그인 사용자의 권한 상태 조회 (API-03-05)
                                .requestMatchers("/api/users/me/seller-requests").authenticated() // 판매자 권한 요청 등록 (API-03-06)
                                .requestMatchers("/api/users/me/role").authenticated() // 판매자 본인의 권한 철회 (API-03-07)

                                // ERROR 디스패치(서블릿이 sendError() 후 내부적으로 /error로 forward할 때)인 경우
                                // Spring Security 필터 체인을 건너뛰고, 원본 에러 상태(403 등)를 그대로 처리하도록 허용
                                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()

                                .anyRequest().authenticated()

                )

                // OAuth2 Login
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(user -> user.userService(customOAuth2UserService()))
                        .successHandler(oAuth2SuccessHandler())
                        .failureHandler(oAuth2LoginFailureHandler()))

                // Login Filter 적용
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, reissueService, cookieUtil),
                        LoginFilter.class)
                .addFilterBefore(new CustomLogoutFilter(jwtTokenProvider, refreshTokenService, cookieUtil),
                        LogoutFilter.class)
                .addFilterAt(new LoginFilter(authenticationManager(authenticationConfiguration), cookieUtil),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AccessKeyFilter(queueRedisAdapter), JwtAuthenticationFilter.class)

                // 인증/인가 실패(인증 실패(401), 권한 부족(403)) 시 반환되는 예외 응답 설정
                .exceptionHandling(exception -> exception
                        // 인증 실패 (401 Unauthorized) 시 처리
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);   // HTTP 401 상태 코드
                            response.getWriter().write("Unauthorized: " + authException.getMessage());  // 응답 메시지
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);   // HTTP 403 상태 코드
                            response.getWriter().write("Access Denied: " + accessDeniedException.getMessage()); // 응답 메시지

                        })
                );

        return http.build();
    }

    /**
     * CORS 설정 빈
     * → 어떤 프론트엔드 주소가 백엔드 API를 호출할 수 있는지 정의
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 허용할 프론트엔드 도메인 (로컬 개발용 및 ngrok 주소)
        // 운영 환경 배포 시에는 실제 서비스 도메인으로 변경
        config.setAllowedOrigins(List.of(
                        Optional.ofNullable(corsProperties.getAllowedOrigins())
                                .orElse(new String[0])
                )
        );

        // 허용할 HTTP 메서드
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 요청 시 허용할 헤더  (인증 관련 헤더 포함)
        config.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "Accept",
                "Origin", "X-CSRF-Token", "Cookie", "Set-Cookie", "X-Access-Key", "ngrok-skip-browser-warning"
        ));

        // 인증 정보(쿠키, HTTP 인증 헤더) 포함한 요청 허용 (프론트엔드에서 credentials: 'include' 필요)
        config.setAllowCredentials(true);
        // Preflight 요청에 대한 캐시 유효 시간 (초)
        config.setMaxAge(3600L);

        // 위 설정을 전체 경로(/)에 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Custom OAuth2UserService 빈
     * → 소셜 로그인(구글, 카카오 등)으로 받은 사용자 정보를 처리하는 서비스
     * → 소셜 로그인 → 사용자 정보 받기 → 우리 DB에 사용자 등록/조회
     */
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService() {
        return new CustomOAuth2UserService(socialUserService, userEntityService);
    }

    /**
     * OAuth2 로그인 성공 핸들러
     * → 소셜 로그인 성공 후: JWT 토큰 발행 → 쿠키에 저장 → 프론트엔드로 리다이렉트
     */
    @Bean
    public OAuth2LoginSuccessHandler oAuth2SuccessHandler() {
        return new OAuth2LoginSuccessHandler(appProperties, userEntityService, cookieUtil);
    }

    /**
     * OAuth2 로그인 실패 핸들러
     * → 소셜 로그인 실패 시: 에러 정보와 함께 프론트엔드의 로그인 페이지로 리다이렉트
     */
    @Bean
    public OAuth2LoginFailureHandler oAuth2LoginFailureHandler() {
        return new OAuth2LoginFailureHandler(appProperties);
    }

}
