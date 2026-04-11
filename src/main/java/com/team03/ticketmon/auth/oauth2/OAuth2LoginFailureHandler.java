package com.team03.ticketmon.auth.oauth2;

import com.team03.ticketmon._global.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2LoginFailureHandler — 소셜 로그인 실패 핸들러
 *
 * 이 핸들러가 하는 일:
 *   1. "need_signup" 에러는 신규 사용자 → 프론트 /register 페이지로 리다이렉트(회원가입 유도)
 *   2. 그 외 실패는 /login 페이지로 리다이렉트
 *   3. 항상 401 상태를 함께 내려 보낸다
 *
 * CustomOAuth2UserService에서 신규 사용자를 감지해 던지는 OAuth2AuthenticationException과 연동된다.
 */
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final AppProperties appProperties;
    private final String REGISTER_URL = "/register";
    private final String LOGIN_URL = "/login";
    private final String NEED_SIGNUP_ERROR_CODE = "need_signup";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        String frontUrl = appProperties.frontBaseUrl();

        if (exception instanceof OAuth2AuthenticationException) {
            OAuth2AuthenticationException ex = (OAuth2AuthenticationException) exception;

            if (NEED_SIGNUP_ERROR_CODE.equals(ex.getError().getErrorCode())) {
                String registerUrl = UriComponentsBuilder
                        .fromUriString(frontUrl + REGISTER_URL)
                        .queryParam("error", NEED_SIGNUP_ERROR_CODE)
                        .build().toUriString();

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.sendRedirect(registerUrl);
                return;
            }
        }

        // 기본 실패 처리
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.sendRedirect(frontUrl + LOGIN_URL);
    }
}
