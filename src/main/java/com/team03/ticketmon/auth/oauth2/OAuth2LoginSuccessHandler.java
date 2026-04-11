package com.team03.ticketmon.auth.oauth2;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.service.UserEntityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Collection;

/**
 * OAuth2LoginSuccessHandler — 소셜 로그인 성공 핸들러
 *
 * 이 핸들러가 하는 일:
 *   1. OAuth2User에서 email을 꺼내 UserEntity를 조회
 *   2. userId/username/role을 구성하여 CookieUtil로 Access/Refresh Token 쿠키 발급
 *   3. 프론트엔드 baseUrl로 리다이렉트하여 로그인 완료
 *
 * Spring Security OAuth2 로그인 성공 지점에서 호출된다.
 */
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AppProperties appProperties;
    private final UserEntityService userEntityService;
    private final CookieUtil cookieUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");

        UserEntity userEntity = userEntityService.findUserEntityByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("소셜 로그인 유저의 이메일 정보가 없습니다."));

        Long userId = userEntity.getId();
        String username = userEntity.getUsername();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities.isEmpty())
            throw new IllegalStateException("사용자 권한 정보가 없습니다.");
        
        GrantedAuthority grantedAuthority = authorities.iterator().next();
        String role = grantedAuthority.getAuthority();

        cookieUtil.generateAndSetJwtCookies(userId, username, role, response);

        String frontendUrl = appProperties.frontBaseUrl();
        response.setStatus(HttpServletResponse.SC_OK);
        response.sendRedirect(frontendUrl);
    }
}
