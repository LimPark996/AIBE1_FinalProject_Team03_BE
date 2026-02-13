package com.team03.ticketmon._global.config.supabase;

import lombok.Getter;
import lombok.Setter;

// → yml 파일의 설정값을 자바 클래스의 필드에 자동으로 매핑(연결)해주는 어노테이션
// → @Value처럼 값을 하나씩 읽는 게 아니라, 관련 설정을 한 클래스에 묶어서 관리
import org.springframework.boot.context.properties.ConfigurationProperties;

// @Profile: 특정 환경에서만 활성화하는 ON/OFF 스위치
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

// @ConfigurationProperties(prefix = "supabase")
// → yml 파일에서 "supabase"로 시작하는 설정값들을 이 클래스의 필드에 자동 매핑함
// @Value와의 차이:
//   @Value: 값을 하나씩 개별적으로 읽어옴 → 변수가 많으면 코드가 지저분해짐
//   @ConfigurationProperties: 관련 값을 한 클래스에 묶어서 관리 → 깔끔하고 재사용 편리
@ConfigurationProperties(prefix = "supabase")

// → Supabase 설정값들을 담는 "데이터 보관함" 클래스
// → 이 클래스 자체는 로직(기능)이 없고, 순수하게 값만 저장하는 역할
// → 이런 클래스를 "POJO(Plain Old Java Object)" 또는 "DTO 비슷한 것"이라고 부르기도 함
public class SupabaseProperties {

    private String url;
    private String key;
    // ──────────────────────────────────────────────────────────
    // 프로필 이미지가 저장될 버킷(Bucket) 이름
    // → yml의 supabase.profile-bucket 값이 여기에 들어옴
    //   (kebab-case → camelCase 자동 변환: profile-bucket → profileBucket)
    //
    // 버킷(Bucket)이란?
    // → 파일을 분류해서 저장하는 "폴더" 같은 개념
    // → 컴퓨터의 폴더처럼, 용도별로 파일을 나눠 보관하는 공간
    // → 예: "profile-images"라는 버킷에는 사용자 프로필 사진만 저장
    // ──────────────────────────────────────────────────────────
    private String profileBucket;
    private String posterBucket;
    private String docsBucket;
}