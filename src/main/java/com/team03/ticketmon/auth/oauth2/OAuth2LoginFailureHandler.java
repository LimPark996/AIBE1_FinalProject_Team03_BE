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

@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final AppProperties appProperties;
    private final String REGISTER_URL = "/register";
    private final String LOGIN_URL = "/login";
    private final String NEED_SIGNUP_ERROR_CODE = "need_signup";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        String frontUrl = appProperties.frontBaseUrl();

        System.out.println("🔥 Exception type: " + exception.getClass().getName());
        System.out.println("🔥 Exception message: " + exception.getMessage());
        System.out.println("🔥 Exception cause: " + exception.getCause());

        if (exception instanceof OAuth2AuthenticationException) {
            System.out.println("🔥 OAuth2AuthenticationException 감지!");

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
        } else {
            System.out.println("🔥 다른 타입의 예외입니다!");
        }

        // 기본 실패 처리
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.sendRedirect(frontUrl + LOGIN_URL);
    }
}
