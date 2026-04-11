package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.domain.entity.RefreshToken;

/**
 * Refresh Token 저장/삭제/검증 서비스 인터페이스. 구현체는 RefreshTokenServiceImpl(Redis 기반).
 */
public interface RefreshTokenService {
    void deleteRefreshToken(Long userId);
    void saveRefreshToken(Long userId, String token);
    void validateRefreshToken(String refreshToken, boolean dbCheck);
    RefreshToken getRefreshToken(Long userId);
}
