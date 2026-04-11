package com.team03.ticketmon.auth.oauth2;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuthAttributes — 소셜 로그인 provider별 사용자 정보 정규화 객체
 *
 * 구글과 카카오의 서로 다른 응답 구조(name/email 위치, nameAttributeKey)를
 * 동일한 필드 셋(name, email, provider, providerId, attributes)으로 변환한다.
 * CustomOAuth2UserService에서 of() 팩터리로 사용된다.
 */
@Getter
@Builder
public class OAuthAttributes {

    private Map<String, Object> attributes;
    private String nameAttributeKey;
    private String name;
    private String email;
    private String provider;
    private String providerId;

    public static final String GOOGLE = "google";
    public static final String KAKAO = "kakao";

    public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {

        switch (registrationId) {
            case GOOGLE :
                return ofGoogle("sub", attributes);
            case KAKAO:
                return ofKakao("id", attributes);
            default:
                throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다: " + registrationId);
        }
    }

    private static OAuthAttributes ofGoogle(String key, Map<String, Object> attrs) {
        return OAuthAttributes.builder()
                .name((String) attrs.get("name"))
                .email((String) attrs.get("email"))
                .attributes(attrs)
                .nameAttributeKey(key)
                .providerId((String) attrs.get(key))
                .provider(GOOGLE)
                .build();
    }

    private static OAuthAttributes ofKakao(String key, Map<String,Object> attrs) {
        Map<String,Object> kakaoAcc = (Map<String,Object>) attrs.get("kakao_account");
        Map<String,Object> profile = (Map<String,Object>) kakaoAcc.get("profile");

        if (kakaoAcc == null || profile == null) {
            throw new OAuth2AuthenticationException("카카오 사용자 정보가 누락되었습니다.");
        }

        String email = (String) kakaoAcc.get("email");
        String name = (String) profile.get("nickname");

        Map<String, Object> flatAttributes = new HashMap<>(attrs);
        flatAttributes.put("email", email);
        flatAttributes.put("name", name);

        return OAuthAttributes.builder()
                .name(name)
                .email(email)
                .attributes(flatAttributes)
                .nameAttributeKey(key)
                .providerId(String.valueOf(attrs.get(key)))
                .provider(KAKAO)
                .build();
    }
}
