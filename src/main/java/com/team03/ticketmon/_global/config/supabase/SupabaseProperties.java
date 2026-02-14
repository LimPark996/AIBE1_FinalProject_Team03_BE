package com.team03.ticketmon._global.config.supabase;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

/**
 * Supabase 설정 값을 application.yml에서 바인딩해주는 구성 클래스입니다.
 *
 * [ 쉬운 설명 ]
 * 이 클래스는 "설정값 보관함" 역할을 함
 * → yml 파일에 적어둔 Supabase 관련 설정값들을 자바 변수에 자동으로 담아주는 그릇
 * → SupabaseConfig 클래스가 이 그릇에서 값을 꺼내 쓰는 구조
 *
 * yml 파일에 이렇게 적으면:
 *   supabase:
 *     url: https://xxx.supabase.co
 *     key: eyJhbGci...
 *     profile-bucket: profile-images
 *     poster-bucket: poster-images
 *     docs-bucket: seller-docs
 *
 * → 이 클래스의 url, key, profileBucket, posterBucket, docsBucket 변수에
 *   각각의 값이 자동으로 들어감!
 */

@Getter
@Setter
@Profile("supabase")

@ConfigurationProperties(prefix = "supabase")

public class SupabaseProperties {

    private String url;
    private String key;
    private String profileBucket;
    private String posterBucket;
    private String docsBucket;
}