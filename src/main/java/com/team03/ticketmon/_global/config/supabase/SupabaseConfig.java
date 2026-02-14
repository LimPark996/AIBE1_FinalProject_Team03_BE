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

@Configuration

@Profile("supabase")

@EnableConfigurationProperties(SupabaseProperties.class)

@RequiredArgsConstructor

public class SupabaseConfig {

    private final SupabaseProperties supabaseProperties;

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