package com.team03.ticketmon._global.util.uploader.s3;

import com.team03.ticketmon._global.exception.StorageUploadException;
import com.team03.ticketmon._global.util.StoragePathProvider;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

/**
 * S3Uploader — AWS S3 기반 파일 업로더 구현체
 *
 * 이 클래스가 하는 일:
 *   1. MultipartFile을 S3 버킷에 PutObject 로 업로드
 *   2. 업로드된 S3 직접 URL을 CloudFront URL로 변환하여 반환
 *   3. public URL로부터 S3 Key 를 추출해 해당 객체를 삭제
 *
 * 예외 처리: IOException / S3Exception / 기타 예외는 모두
 * {@link StorageUploadException} 으로 래핑되어 상위로 전파됩니다.
 * 활성 조건: Spring Profile "s3" 가 활성화된 경우에만 빈으로 등록됩니다.
 */
@Slf4j
@Component
@Profile("s3")
@RequiredArgsConstructor
public class S3Uploader implements StorageUploader {

    private final S3Client s3Client;
    private final StoragePathProvider storagePathProvider; // StoragePathProvider 주입

    @Override
    public String uploadFile(MultipartFile file, String bucket, String path) {
        if (file.isEmpty()) {
            throw new StorageUploadException("업로드할 파일이 비어 있습니다.");
        }

        String s3Key = path; // `path`는 이미 S3 키 형식으로 제공

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket) // 인터페이스에서 받은 bucket 이름을 사용
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // 먼저 S3 직접 URL 생성
            String s3DirectUrl = String.format("https://%s.s3.%s.amazonaws.com/%s",
                    bucket,
                    s3Client.serviceClientConfiguration().region().id(),
                    s3Key);

            // CloudFront URL로 변환하여 반환
            String cloudFrontUrl = storagePathProvider.getCloudFrontImageUrl(s3DirectUrl);

            log.info("S3 파일 업로드 성공: s3DirectUrl={}, cloudFrontUrl={}", s3DirectUrl, cloudFrontUrl);
            return cloudFrontUrl; // CloudFront URL 반환

        } catch (IOException e) {
            log.error("S3 파일 스트림 처리 중 오류 발생", e);
            throw new StorageUploadException("파일 스트림 처리 중 오류 발생", e);
        } catch (S3Exception e) {
            log.error("S3 업로드 중 오류 발생: {}", e.getMessage(), e);
            throw new StorageUploadException("S3 업로드 중 오류 발생: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("파일 업로드 중 알 수 없는 오류 발생", e);
            throw new StorageUploadException("파일 업로드 중 알 수 없는 오류 발생", e);
        }
    }

    @Override
    public void deleteFile(String bucket, String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            log.warn("삭제할 파일 경로가 비어 있습니다.");
            return;
        }

        // Public URL에서 S3 Key 추출 시, 주입받은 storagePathProvider 사용
        String s3KeyToDelete = storagePathProvider.extractPathFromPublicUrl(fullPath, bucket)
                .orElse(null);

        if (s3KeyToDelete == null || s3KeyToDelete.isEmpty()) {
            log.warn("Public URL에서 S3 Key 추출 실패: fullPath={}", fullPath);
            return;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3KeyToDelete)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("S3 파일 삭제 성공: bucket={}, key={}", bucket, s3KeyToDelete);

        } catch (S3Exception e) {
            log.error("S3 파일 삭제 중 오류 발생: {}", e.getMessage(), e);
            throw new StorageUploadException("S3 파일 삭제 중 오류 발생: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("파일 삭제 중 알 수 없는 오류 발생", e);
            throw new StorageUploadException("파일 삭제 중 알 수 없는 오류 발생", e);
        }
    }
}