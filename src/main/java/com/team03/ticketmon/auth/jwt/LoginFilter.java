package com.team03.ticketmon.auth.jwt;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * LoginFilter — 폼 기반 로그인 인증 필터 (JWT 발급 포함)
 *
 * 이 필터가 하는 일:
 *   1. POST /api/auth/login 요청에서 username/password를 꺼냄
 *   2. AuthenticationManager에 위임해 CustomUserDetailsService로 사용자 인증
 *   3. 인증 성공 시 CookieUtil로 Access/Refresh Token을 발급해 응답 쿠키에 심음 (200 OK)
 *   4. 인증 실패 시 401 Unauthorized 반환
 *
 * Spring Security의 UsernamePasswordAuthenticationFilter를 상속하여
 * 기본 URL을 /api/auth/login으로 재설정하고, 성공 핸들링을 JWT 쿠키 발급으로 대체한다.
 */
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final CookieUtil cookieUtil;

    public LoginFilter(AuthenticationManager authenticationManager, CookieUtil cookieUtil) {
        this.authenticationManager = authenticationManager;
        this.cookieUtil = cookieUtil;
        setFilterProcessesUrl("/api/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        if (!"POST".equalsIgnoreCase(request.getMethod()))
            throw new AuthenticationCredentialsNotFoundException("Authentication Method가 POST 요청이 아닙니다");

        // 클라이언트 요청에서 아이디, 비밀번호 추출
        String username = obtainUsername(request);
        String password = obtainPassword(request);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password, null);

        return authenticationManager.authenticate(authToken);
    }

    // 로그인 성공 시 실행하는 메소드 (여기서 JWT를 발급)
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) {
        CustomUserDetails userDetails = (CustomUserDetails) authResult.getPrincipal();
        Long userId = userDetails.getUserId();
        String username = userDetails.getUsername();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        cookieUtil.generateAndSetJwtCookies(userId, username, role, response);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    // 로그인 실패시 실행하는 메소드
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
