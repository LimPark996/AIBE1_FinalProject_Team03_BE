package com.team03.ticketmon.auth.service;

import com.team03.ticketmon.auth.oauth2.OAuthAttributes;
import com.team03.ticketmon.user.domain.entity.SocialUser;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.service.SocialUserService;
import com.team03.ticketmon.user.service.UserEntityService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Optional;

/**
 * CustomOAuth2UserService — 소셜 로그인 사용자 처리 서비스
 *
 * 이 서비스가 하는 일:
 *   1. DefaultOAuth2UserService를 위임 호출해 provider(구글/카카오)의 사용자 정보를 로드
 *   2. OAuthAttributes로 정규화 후 provider/providerId/email 필수값 검증
 *   3. SocialUser가 이미 존재 → 기존 계정으로 로그인 처리
 *   4. 이메일 기반으로 기존 로컬 계정이 존재 → SocialUser만 추가 연결 후 로그인 처리
 *   5. 완전한 신규 사용자 → 세션에 OAuthAttributes를 저장하고 "need_signup" 에러를 던져
 *      OAuth2LoginFailureHandler가 프론트 회원가입 페이지로 리다이렉트하도록 유도
 *
 * Spring Security OAuth2 로그인 플로우의 사용자 조회 단계에서 호출된다.
 */
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final SocialUserService socialUserService;
    private final UserEntityService userEntityService;

    @SuppressWarnings("unchecked")
    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = delegate.loadUser(request);
        String regId = request.getClientRegistration().getRegistrationId();
        OAuthAttributes attr = OAuthAttributes.of(regId, oAuth2User.getAttributes());

        // 유효성 확인
        validateOAuthAttributes(attr);

        // 소셜 계정이 이미 존재
        Optional<SocialUser> socialOpt = socialUserService.findByProviderAndProviderId(attr.getProvider(), attr.getProviderId());
        if (socialOpt.isPresent()) {
            UserEntity user = socialOpt.get().getUserEntity();

            return new DefaultOAuth2User(
                    Collections.singleton(new SimpleGrantedAuthority(user.getRole().getRoleName())),
                    attr.getAttributes(),
                    attr.getNameAttributeKey()
            );
        }

        // 이메일 기반 기존 사용자 존재 여부 확인
        var userOpt = userEntityService.findUserEntityByEmail(attr.getEmail());
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            socialUserService.saveSocialUser(user, attr);

            return new DefaultOAuth2User(
                    Collections.singleton(new SimpleGrantedAuthority(user.getRole().getRoleName())),
                    attr.getAttributes(),
                    attr.getNameAttributeKey()
            );
        }

        // 신규 사용자 -> 회원가입 유도
        newAccountSave(attr);
        OAuth2Error oauth2Error = new OAuth2Error("need_signup", "유저 정보가 없어 회원가입이 필요합니다.", null);
        throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
    }

    private void validateOAuthAttributes(OAuthAttributes attr) {
        if (attr.getProvider() == null || attr.getProviderId() == null) {
            OAuth2Error oauth2Error = new OAuth2Error("invalid_oauth_attributes", "OAuth 제공자 정보가 누락되었습니다.", null);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }

        if (attr.getEmail() == null || attr.getEmail().isEmpty()) {
            OAuth2Error oauth2Error = new OAuth2Error("missing_email", "이메일 정보가 제공되지 않았습니다.", null);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
    }

    private void newAccountSave(OAuthAttributes attr) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            HttpSession session = ((ServletRequestAttributes) requestAttributes).getRequest().getSession();
            session.setAttribute("oauthAttributes", attr);
        }
    }
}
