package com.team03.ticketmon.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Access/Refresh Token 재발급 서비스 인터페이스. 구현체는 ReissueServiceImpl.
 */
public interface ReissueService {
    String reissueToken(String refreshToken, String reissueCategory, boolean dbCheck);
    void handleReissueToken(HttpServletRequest request, HttpServletResponse response);
}
