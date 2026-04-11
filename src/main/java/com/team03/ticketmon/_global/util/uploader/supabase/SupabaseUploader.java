package com.team03.ticketmon._global.util.uploader.supabase;

import com.team03.ticketmon._global.exception.StorageUploadException;
import com.team03.ticketmon._global.util.StoragePathProvider;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import io.supabase.StorageClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor; // RequiredArgsConstructor 어노테이션 추가

/**
 * SupabaseUploader — Supabase Storage 기반 파일 업로더 구현체
 *
 * 이 클래스가 하는 일:
 *   1. MultipartFile을 임시 파일로 저장 후 Supabase Storage에 업로드
 *   2. 업로드된 파일의 public URL을 반환
 *   3. public URL로부터 경로를 추출해 파일 삭제 수행
 *   4. 업로드 완료/실패와 무관하게 임시 파일을 정리
 *
 * 예외 처리: IO / Interrupted / Execution 예외는 모두
 * {@link StorageUploadException} 으로 래핑되어 상위로 전파됩니다.
 * 활성 조건: Spring Profile "supabase" 가 활성화된 경우에만 빈으로 등록됩니다.
 */
@Slf4j
@Component
@Profile("supabase")
@RequiredArgsConstructor // 생성자 자동 주입을 위해 추가
public class SupabaseUploader implements StorageUploader {

    private final StorageClient storageClient;
    private final StoragePathProvider storagePathProvider; // StoragePathProvider 주입

    // 기존 생성자는 RequiredArgsConstructor로 대체됩니다.

    @Override
    public String uploadFile(MultipartFile file, String bucket, String finalUploadPath) {
        File tempFile = null;
        try {
            String fullPath = finalUploadPath;

            String fileExtension = "";
            if (file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")) {
                fileExtension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'));
            }
            tempFile = File.createTempFile("upload-", fileExtension);
            file.transferTo(tempFile);

            URI uri = tempFile.toURI();
            File safeFile = new File(uri);

            log.debug("✅ [DEBUG] SupabaseUploader 업로드 시작");
            log.debug("✅ [DEBUG] bucket = {}", bucket);
            log.debug("✅ [DEBUG] finalUploadPath (received) = {}", finalUploadPath);
            log.debug("✅ [DEBUG] fullPath (used for upload) = {}", fullPath);

            storageClient.from(bucket)
                    .upload(fullPath, safeFile)
                    .get();

            String publicUrl = storageClient.from(bucket)
                    .getPublicUrl(fullPath, null, null)
                    .getPublicUrl();

            log.debug("✅ [DEBUG] public URL = {}", publicUrl);

            return publicUrl;

        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("❌ 파일 업로드 중 시스템 또는 기타 예외 발생", e);
            throw new StorageUploadException("파일 업로드 중 시스템 오류", e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (deleted) {
                    log.debug("🧹 임시 파일 삭제 성공: {}", tempFile.getAbsolutePath());
                } else {
                    log.warn("❗ 임시 파일 삭제 실패: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    @Override
    public void deleteFile(String bucket, String fullPath) {
        try {
            log.debug("✅ [DEBUG] SupabaseUploader 파일 삭제 시작");
            log.debug("✅ [DEBUG] bucket = {}", bucket);
            log.debug("✅ [DEBUG] fullPath = {}", fullPath);

            // 주입받은 storagePathProvider를 사용하여 경로 추출
            String deletePath = storagePathProvider.extractPathFromPublicUrl(fullPath, bucket).orElse(null);
            log.debug("✅ [DEBUG] deletePath = {}", deletePath);

            if (deletePath == null || deletePath.isEmpty()) {
                log.warn("❗ Supabase 파일 삭제 실패: {}", fullPath);
                throw new IllegalArgumentException("파일 경로 형식이 잘못되었습니다.");
            }

            storageClient.from(bucket).delete(List.of(deletePath)).get();
            log.info("🗑️ Supabase 파일 삭제 성공: {}", fullPath);
        } catch (InterruptedException | ExecutionException e) {
            log.warn("❗ Supabase 파일 삭제 실패: {}", fullPath, e);
            throw new StorageUploadException("파일 삭제 실패", e);
        }
    }
}