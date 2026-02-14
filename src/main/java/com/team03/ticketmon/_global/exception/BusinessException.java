package com.team03.ticketmon._global.exception;

import lombok.Getter;

/**
 * BusinessException: 커스텀 비즈니스 예외 처리 클래스
 *
 * 쉽게 말해, "우리 서비스에서 의도적으로 발생시키는 에러"를 담당하는 클래스
 * 예: 로그인 실패, 좌석 이미 선점됨, 콘서트를 찾을 수 없음 등
 *
 * 자바 기본 Exception은 에러 메시지밖에 없어서
 * HTTP 상태코드, 에러코드 등을 함께 전달하기 어려움
 * → 그래서 이 클래스를 만들어 ErrorCode와 함께 사용하는 것!
 *
 * 사용 예시:
 * throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
 * → "리소스를 찾을 수 없습니다" 에러를 발생시킴
 */

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String customMessage;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());  // 예외 메시지는 ErrorCode에서 가져옴
        this.errorCode = errorCode;
		this.customMessage = null;
	}

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage != null ? customMessage : errorCode.getMessage());
        this.errorCode = errorCode;
        this.customMessage = customMessage;
    }
}