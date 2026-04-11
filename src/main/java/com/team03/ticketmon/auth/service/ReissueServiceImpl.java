package com.team03.ticketmon.auth.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ReissueServiceImpl — Access/Refresh Token 재발급 서비스
 *
 * 이 서비스가 하는 일:
 *   1. reissueToken: 전달된 Refresh Token을 검증한 뒤 같은 사용자 정보로 새 Access(또는 Refresh) 토큰 생성
 *   2. handleReissueToken: 요청 쿠키에서 Refresh Token을 꺼내 검증하고,
 *      CookieUtil로 Access/Refresh를 모두 새로 발급해 응답 쿠키에 주입 (Refresh Rotation)
 *
 * JwtAuthenticationFilter(자동 재발급)와 명시적 재발급 API 양쪽에서 호출된다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReissueServiceImpl implements ReissueService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    @Override
    public String reissueToken(String refreshToken, String reissueCategory, boolean dbCheck) {
        refreshTokenService.validateRefreshToken(refreshToken, dbCheck);
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        String role = extractRole(refreshToken);
        return jwtTokenProvider.generateToken(reissueCategory, userId, username, role);
    }

    @Override
    public void handleReissueToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request);
        if (refreshToken == null || refreshToken.isEmpty())
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh Token이 존재하지 않습니다.");

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        String role = extractRole(refreshToken);

        refreshTokenService.validateRefreshToken(refreshToken, true);

        cookieUtil.generateAndSetJwtCookies(userId, username, role, response);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String extractRole(String token) {
        List<String> roles = jwtTokenProvider.getRoles(token);
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "역할(Role) 정보가 없습니다.");
        }
        return roles.get(0);
    }
}
