package com.team03.ticketmon._global.config.supabase;

import io.supabase.StorageClient;   // 1.1.0
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Supabase Storage SDK를 초기화하고, StorageClient를 스프링 빈으로 등록하는 설정 클래스입니다.
 *
 * [ 쉬운 설명 ]
 * - Supabase: Firebase와 비슷한 클라우드 서비스 (데이터베이스, 파일 저장소 등 제공)
 * - Storage SDK: Supabase의 파일 저장소를 자바에서 쉽게 사용하게 해주는 도구 모음
 * - 이 클래스는 그 도구(StorageClient)를 만들어서 스프링에게 맡기는 역할
 *
 * - SupabaseProperties에 정의된 설정 값(url, key)을 사용하여 StorageClient를 구성하며,
 *   이후 업로더 클래스(SupabaseUploader 등)에서 주입받아 파일 업로드에 활용됩니다.
 */

// → 스프링에게 "이 클래스는 설정(Config) 클래스야"라고 알려줌
// → 스프링이 앱 시작할 때 이 클래스를 자동으로 읽고 처리함
@Configuration

// → "supabase"라는 프로필(환경)이 활성화되어 있을 때만 이 클래스가 동작함
// → application.yml에서 spring.profiles.include: supabase 로 설정해야 켜짐
// → S3Config에는 @Profile("s3")가 있었음 → 둘 중 하나만 활성화해서 사용하는 구조
//   (S3 쓸 때는 "s3" 프로필, Supabase 쓸 때는 "supabase" 프로필)
@Profile("supabase")

// → SupabaseProperties라는 클래스를 스프링이 인식하도록 "활성화"해줌
// → SupabaseProperties 클래스에는 yml에서 읽어온 url, key 등의 설정값이 담겨 있음
// → 쉽게 말해: "SupabaseProperties 클래스도 스프링이 관리해줘!"라는 뜻
// → 이걸 안 하면 SupabaseProperties를 주입받을 수 없어서 에러 발생
@EnableConfigurationProperties(SupabaseProperties.class)

// → final로 선언된 필드를 파라미터로 받는 생성자를 자동으로 만들어줌
// → 이 클래스에는 아래에 final로 선언된 supabaseProperties가 있으므로,
//   Lombok이 자동으로 다음과 같은 생성자를 만들어줌:
//
//   public SupabaseConfig(SupabaseProperties supabaseProperties) {
//       this.supabaseProperties = supabaseProperties;
//   }
//
// → 스프링은 이 생성자를 보고 SupabaseProperties 객체를 자동으로 넣어줌 (의존성 주입)
// → 즉, 개발자가 직접 생성자 코드를 안 써도 됨! 코드가 깔끔해짐
@RequiredArgsConstructor

// → Supabase 파일 저장소에 접속하기 위한 설정을 담당하는 클래스
public class SupabaseConfig {

    private final SupabaseProperties supabaseProperties;

    /**
     * Supabase StorageClient를 빈으로 등록합니다.
     * - 이 StorageClient는 파일 업로드/삭제/다운로드 등에 사용됩니다.
     *
     * @return StorageClient 인스턴스 (Supabase 파일 저장소 접속 도구)
     */
    @Bean
    public StorageClient storageClient() {

        String baseUrl = supabaseProperties.getUrl();

        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";  // baseUrl = baseUrl + "/" 와 같은 뜻 (문자열 이어붙이기)
        }

        String storageUrl = baseUrl + "storage/v1/";

        System.out.println("[DEBUG] Supabase storageUrl = " + storageUrl);

        System.out.println("[DEBUG] Supabase key (first 5 chars) = "
                + supabaseProperties.getKey().substring(0, Math.min(supabaseProperties.getKey().length(), 5)));

        // 최종적으로 StorageClient 객체를 만들어서 반환(return)함
        return new StorageClient(supabaseProperties.getKey(), storageUrl);
    }
}