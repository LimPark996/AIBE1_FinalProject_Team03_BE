package com.team03.ticketmon.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.util.RedisKeyGenerator;
import com.team03.ticketmon.auth.domain.entity.RefreshToken;
import com.team03.ticketmon.auth.jwt.JwtTokenProvider;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * RefreshTokenServiceImpl — Refresh Token 저장소 관리 서비스
 *
 * 이 서비스가 하는 일:
 *   1. Refresh Token을 Redis에 사용자별 키(JWT_RT_PREFIX + userId)로 저장/삭제
 *   2. 전달된 Refresh Token의 카테고리/만료/DB(=Redis) 존재 여부를 검증
 *   3. 검증 실패 시 BusinessException(INVALID_TOKEN) 발생
 *
 * 로그인/로그아웃/토큰 재발급 흐름에서 CookieUtil, ReissueService, CustomLogoutFilter 등과 연동된다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void saveRefreshToken(Long userId, String token) {
        if (!userRepository.existsById(userId))
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);

        RefreshToken refreshToken = RefreshToken.builder()
                .id(userId)
                .token(token)
                .created_at(LocalDateTime.now())
                .build();

        String redisKey = RedisKeyGenerator.JWT_RT_PREFIX + userId;
        redisTemplate.opsForValue().set(redisKey, refreshToken, refreshExpirationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(RedisKeyGenerator.JWT_RT_PREFIX + userId);
    }

    @Override
    public void validateRefreshToken(String refreshToken, boolean dbCheck) {
        String category = jwtTokenProvider.getCategory(refreshToken);
        if (!jwtTokenProvider.CATEGORY_REFRESH.equals(category))
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 카테고리 JWT 토큰입니다.");

        if (jwtTokenProvider.isTokenExpired(refreshToken))
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh Token이 만료되었습니다.");

        if (dbCheck) {
            Long userId = jwtTokenProvider.getUserId(refreshToken);
            RefreshToken storedToken = getRefreshToken(userId);
            if (storedToken == null || !storedToken.getToken().equals(refreshToken))
                throw new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh Token이 존재하지 않습니다.");
        }
    }

    @Override
    public RefreshToken getRefreshToken(Long userId) {
        String key = RedisKeyGenerator.JWT_RT_PREFIX + userId;
        Object value = redisTemplate.opsForValue().get(key);
        return objectMapper.convertValue(value, RefreshToken.class);
    }
}
