package com.team03.ticketmon.auth.jwt;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.service.ReissueService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter — JWT 쿠키 기반 인증 필터
 *
 * 이 필터가 하는 일:
 *   1. 요청 쿠키에서 Access Token / Refresh Token 추출
 *   2. Access Token이 유효하면 SecurityContextHolder에 Authentication 주입
 *   3. Refresh Token이 없거나 만료된 경우 재발급 시도 없이 그대로 통과 (비인증 상태)
 *   4. Access Token이 만료되고 Refresh Token이 유효하면 ReissueService로 새 Access Token 발급 후 쿠키 갱신
 *   5. 재발급 실패 시 쿠키 삭제 + 401 응답으로 재로그인 유도
 *
 * Spring Security 필터 체인에서 UsernamePasswordAuthenticationFilter 앞에 위치하여
 * 모든 요청에 대해 토큰 기반 인증을 적용한다. OncePerRequestFilter를 상속하며,
 * Async Dispatch(DeferredResult 재디스패치) 시에도 동작하도록 설정되어 있다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final ReissueService reissueService;
    private final CookieUtil cookieUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String accessToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_ACCESS, request);
        String refreshToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request);

        boolean noOrExpiredRefresh =
                (refreshToken == null || jwtTokenProvider.isTokenExpired(refreshToken));

        log.debug("[JWT Filter] accessToken={} expired? {}",
                accessToken, accessToken == null ? "n/a" : jwtTokenProvider.isTokenExpired(accessToken));
        log.debug("[JWT Filter] refreshToken={} expired? {}",
                refreshToken, refreshToken == null ? "n/a" : jwtTokenProvider.isTokenExpired(refreshToken));
        log.debug("[JWT Filter] noOrExpiredRefresh={}", noOrExpiredRefresh);


        // Access만 유효하면 인증 컨텍스트 설정
        if (!isEmpty(accessToken) && !jwtTokenProvider.isTokenExpired(accessToken)) {
            setAuthenticationContext(accessToken);
        }
        if (noOrExpiredRefresh) {
            // Refresh 재발급 시도 없이 그냥 체인 계속
            filterChain.doFilter(request, response);
            return;
        }

        // Access Token이 유효하다면 그대로 인증 처리
        if (!isEmpty(accessToken) && !jwtTokenProvider.isTokenExpired(accessToken)) {
            setAuthenticationContext(accessToken);
            filterChain.doFilter(request, response);
            return;
        }

        // Access Token이 만료되었고 Refresh Token 유효성 확인 후 재발급
        accessToken = handleTokenReissue(refreshToken, response);

        if (isEmpty(accessToken)) return;

        if (!isAccessToken(accessToken, response)) return;

        setAuthenticationContext(accessToken);
        filterChain.doFilter(request, response);
    }

    /**
     * Refresh Token으로 새 Access Token을 발급받아 응답 쿠키에 심어준다.
     * 실패 시 기존 JWT 쿠키를 모두 지우고 401을 내려보낸다.
     */
    private String handleTokenReissue(String refreshToken, HttpServletResponse response) throws IOException {
        log.info("Access Token 만료됨 Refresh Token으로 재발급 시도");

        try {
            String newAccessToken = reissueService.reissueToken(refreshToken, jwtTokenProvider.CATEGORY_ACCESS, true);

            if (isEmpty(newAccessToken)) {
                log.warn("Refresh Token이 유효하지 않음 재로그인 필요");
                cookieUtil.deleteJwtCookies(response);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh Token이 만료되었거나 유효하지 않습니다.");
                return null;
            }

            Long accessExp = jwtTokenProvider.getExpirationMs(jwtTokenProvider.CATEGORY_ACCESS);
            ResponseCookie accessCookie = cookieUtil.createCookie(jwtTokenProvider.CATEGORY_ACCESS, newAccessToken, accessExp);
            response.addHeader("Set-Cookie", accessCookie.toString());
            return newAccessToken;

        } catch (Exception e) {
            log.warn("Access Token 재발급 실패: {}", e.getMessage());
            cookieUtil.deleteJwtCookies(response);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh Token이 만료되었거나 유효하지 않습니다.");
            return null;
        }
    }

    private boolean isAccessToken(String token, HttpServletResponse response) throws IOException {
        try {
            String category = jwtTokenProvider.getCategory(token);
            return jwtTokenProvider.CATEGORY_ACCESS.equals(category);
        } catch (Exception e) {
            log.error("Access Token 파싱 실패 : {}", e.getMessage());
            cookieUtil.deleteJwtCookies(response);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access Token 파싱에 실패했습니다.");
            return false;
        }
    }

    // 인증 객체 등록
    private void setAuthenticationContext(String token) {
        Authentication auth = jwtTokenProvider.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Async Dispatch(=DeferredResult 재디스패치) 시에도 필터를 실행하도록 허용
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

}
