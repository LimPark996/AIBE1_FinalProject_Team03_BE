package com.team03.ticketmon.auth.jwt;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

/**
 * CustomLogoutFilter — 로그아웃 요청 처리 필터
 *
 * 이 필터가 하는 일:
 *   1. POST /api/auth/logout 요청만 가로채고 그 외는 체인 통과
 *   2. 쿠키에서 Refresh Token을 꺼내 유효성 검증(DB 체크 포함)
 *   3. 검증 통과 시 Redis에 저장된 Refresh Token 삭제 → 서버측 세션 무효화
 *   4. 성공/실패 여부와 무관하게 최종적으로 Access/Refresh 쿠키를 삭제하여 클라이언트 로그아웃 완료
 *
 * Spring Security 필터 체인에서 LogoutFilter 위치에 등록되어 JWT 기반 로그아웃을 구현한다.
 */
@RequiredArgsConstructor
public class CustomLogoutFilter extends GenericFilterBean {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (isLogoutRequest(request)) {
            handleLogout(request, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request);
        if (refreshToken == null || refreshToken.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            refreshTokenService.validateRefreshToken(refreshToken, true);

            Long userId = jwtTokenProvider.getUserId(refreshToken);

            refreshTokenService.deleteRefreshToken(userId);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } finally {
            cookieUtil.deleteJwtCookies(response);
        }
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();

        return "/api/auth/logout".equals(requestUri) && "POST".equals(requestMethod);
    }
}
