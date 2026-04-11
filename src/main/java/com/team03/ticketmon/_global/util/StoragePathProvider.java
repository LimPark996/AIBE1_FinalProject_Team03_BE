package com.team03.ticketmon._global.util;

import java.util.Optional;

/**
 * StoragePathProvider — 스토리지 경로 제공 공통 인터페이스
 *
 * S3, Supabase 등 저장소 종류에 상관없이 동일한 방식으로 파일 경로/버킷명/public URL 변환을
 * 요청할 수 있도록 하는 추상화 계층입니다. 프로파일에 따라 {@code S3PathProvider} 또는
 * {@code SupabasePathProvider} 구현체가 주입됩니다.
 */
public interface StoragePathProvider {
    // 파일 종류별 저장 경로 생성 (예: profile-imgs/uuid.jpg)
    String getProfilePath(String uuid, String fileExtension);
    String getPosterPath(Long concertId, String fileExtension);
    String getSellerDocsPath(String uuid, String fileExtension);

    // 파일 종류별 버킷(또는 최상위 컨테이너) 이름 반환
    String getProfileBucketName();
    String getPosterBucketName();
    String getDocsBucketName();

    // Public URL에서 실제 파일 경로(객체 키) 추출
    Optional<String> extractPathFromPublicUrl(String publicUrl, String bucketName);

    /**
     * S3 직접 URL을 CloudFront를 통한 이미지 URL로 변환합니다.
     *
     * @param s3DirectUrl S3 버킷의 직접적인 이미지 URL (예: https://bucket.s3.region.amazonaws.com/key)
     * @return CloudFront를 통한 이미지 URL (예: https://your-cloudfront-domain.com/key) 또는 null/기본값
     */
    String getCloudFrontImageUrl(String s3DirectUrl);
}