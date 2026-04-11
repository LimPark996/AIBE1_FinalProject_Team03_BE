package com.team03.ticketmon._global.exception;

/**
 * StorageUploadException — 파일 업로드/삭제 중 발생한 시스템 예외
 *
 * S3/Supabase 등 외부 스토리지 연동 과정에서 발생한 IO/네트워크/SDK 예외를
 * 래핑해 상위 계층으로 전달합니다. GlobalExceptionHandler에서 500 응답으로 변환됩니다.
 */
public class StorageUploadException extends RuntimeException {
    public StorageUploadException(String message) {
        super(message);
    }

    public StorageUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
