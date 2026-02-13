package com.team03.ticketmon._global.config.s3;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * AWS S3 연동을 위한 설정 클래스 (마이그레이션 대비 스켈레톤)
 *
 * <p>
 * "스켈레톤"이란 뼈대만 미리 만들어 둔 코드라는 뜻
 * 지금은 실제로 사용하지 않지만, 나중에 파일 저장소를
 * 로컬 → S3로 변경(마이그레이션)할 때 바로 쓸 수 있도록 준비해 둔 것
 *
 * 사용하려면: application.yml에서 spring.profiles.include를 "s3"로 바꾸면 됨
 * </p>
 */

@Profile("s3")
@Configuration

// ──────────────────────────────────────────────────────────────
// public class S3Config
// → 이 클래스 안에 S3 연결에 필요한 설정 코드들이 들어감
// ──────────────────────────────────────────────────────────────
public class S3Config {

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Bean
    public S3Client s3Client() {

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }
}