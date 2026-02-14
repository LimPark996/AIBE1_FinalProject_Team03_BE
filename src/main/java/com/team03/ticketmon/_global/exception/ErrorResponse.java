// 이 클래스가 속한 패키지 선언
package com.team03.ticketmon._global.exception;

// @JsonInclude: Jackson 라이브러리의 어노테이션
// JSON으로 변환할 때 어떤 필드를 포함할지 제어함
// Jackson이란? 자바 객체를 JSON으로 변환하거나, JSON을 자바 객체로 변환하는 라이브러리
import com.fasterxml.jackson.annotation.JsonInclude;
// @Getter: 모든 필드의 getter 메서드 자동 생성
import lombok.Getter;
// HttpStatus: 스프링에서 제공하는 HTTP 상태 코드 enum (200 OK, 404 NOT_FOUND 등)
import org.springframework.http.HttpStatus;
// BindingResult: @Valid 어노테이션으로 입력값 검증 실패 시, 어떤 필드가 왜 실패했는지 담고 있는 객체
// 예: "이메일 형식이 올바르지 않습니다", "이름은 필수입니다" 등의 검증 결과
import org.springframework.validation.BindingResult;

// List: 여러 개의 데이터를 순서대로 저장하는 자료구조 (파이썬의 리스트와 비슷)
import java.util.List;
// Collectors: Stream의 결과를 List 등으로 모아주는 유틸리티 클래스
import java.util.stream.Collectors;

/**
 * ErrorResponse: 에러 응답을 통일된 형식으로 만들어주는 클래스
 *
 * API에서 에러가 발생하면, 프론트엔드에게 항상 같은 형태의 JSON으로 응답을 보냄
 * 이렇게 하면 프론트엔드 개발자가 에러 처리를 일관되게 할 수 있음
 *
 * 응답 JSON 예시:
 * {
 *   "success": false,
 *   "status": 404,
 *   "code": "C002",
 *   "message": "리소스를 찾을 수 없습니다."
 * }
 */

// @Getter: 모든 필드의 getter 자동 생성
@Getter

// @JsonInclude(JsonInclude.Include.NON_NULL):
// JSON으로 변환할 때, 값이 null인 필드는 아예 JSON에 포함시키지 않음
// 예: errors가 null이면 응답 JSON에 "errors" 키 자체가 없음 → 깔끔한 응답!
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    // 항상 false로 고정 (에러 응답이니까!)
    // 성공 응답은 SuccessResponse에서 true로 보냄
    // final: 한번 false로 정해지면 변경 불가
    private final boolean success = false;

    // HTTP 상태 코드 (숫자) → 예: 400, 401, 404, 500
    private final int status;

    // 내부 비즈니스 에러 코드 (문자열) → 예: "C001", "A003"
    private final String code;

    // 사용자에게 보여줄 에러 메시지
    private final String message;

    // 입력값 검증 에러 목록 (선택사항)
    // @Valid 검증 실패 시에만 사용됨
    // 예: [{"field": "email", "message": "이메일 형식이 올바르지 않습니다"}]
    // final이 아니므로 나중에 값을 넣을 수 있음
    private List<ValidationError> errors;

    // ──────────────────────────────────────────────
    //  생성자들 (모두 private → 외부에서 new로 직접 생성 불가)
    //  대신 아래의 of() 메서드(정적 팩토리 메서드)를 통해 생성함
    // ──────────────────────────────────────────────

    /**
     * 생성자 1: ErrorCode enum을 받아서 생성
     * 가장 기본적인 에러 응답을 만듦
     *
     * private: 클래스 외부에서 직접 호출 불가 → of() 메서드를 통해서만 생성
     */
    private ErrorResponse(ErrorCode errorCode) {
        // ErrorCode에서 각 값을 꺼내서 필드에 저장
        this.status = errorCode.getStatus();   // 예: 404
        this.code = errorCode.getCode();       // 예: "C002"
        this.message = errorCode.getMessage(); // 예: "리소스를 찾을 수 없습니다."
    }

    /**
     * 생성자 2: ErrorCode + 검증 에러 리스트를 받아서 생성
     * 입력값 검증(@Valid) 실패 시 각 필드별 에러 정보를 함께 담을 때 사용
     */
    private ErrorResponse(ErrorCode errorCode, List<ValidationError> errors) {
        this.status = errorCode.getStatus();
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage(); // "유효하지 않은 입력값입니다" 같은 포괄적 메시지
        this.errors = errors; // 구체적인 필드별 에러 정보 리스트
    }

    /**
     * 생성자 3: ErrorCode + 커스텀 메시지를 받아서 생성
     * ErrorCode의 기본 메시지 대신 더 구체적인 메시지를 보내고 싶을 때 사용
     */
    private ErrorResponse(ErrorCode errorCode, String customMessage) {
        this.status = errorCode.getStatus();
        this.code = errorCode.getCode();
        // 삼항 연산자: customMessage가 null이 아니면 customMessage를, null이면 기본 메시지를 사용
        this.message = customMessage != null ? customMessage : errorCode.getMessage();
    }

    /**
     * 생성자 4: HTTP 상태코드, 에러코드, 메시지를 직접 받아서 생성
     * 미리 정의된 ErrorCode에 없는 예외 상황을 처리할 때 사용
     */
    private ErrorResponse(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    // ──────────────────────────────────────────────
    //  정적 팩토리 메서드 (of 메서드들)
    //
    //  "정적 팩토리 메서드" 패턴이란?
    //  → 생성자를 private으로 숨기고, static 메서드로 객체를 생성하는 방식
    //  → 장점1: 메서드 이름으로 의도를 명확히 표현 가능
    //  → 장점2: 매개변수 조합에 따라 다양한 생성 방식 제공 가능
    //
    //  사용법: ErrorResponse.of(ErrorCode.LOGIN_FAILED)
    //  (new ErrorResponse(...)가 아니라 of(...)로 만듦)
    // ──────────────────────────────────────────────

    /**
     * 팩토리 메서드 1: ErrorCode만으로 에러 응답 생성
     * 가장 많이 사용되는 형태
     *
     * static: 객체를 만들지 않아도 클래스명으로 바로 호출 가능
     * 예: ErrorResponse.of(ErrorCode.LOGIN_FAILED)
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }

    /**
     * 팩토리 메서드 2: ErrorCode + 커스텀 메시지로 에러 응답 생성
     * 기본 메시지를 덮어쓰고 싶을 때 사용
     *
     * 예: ErrorResponse.of(ErrorCode.SEAT_ALREADY_TAKEN, "A구역 5번 좌석은 이미 선택됨")
     */
    public static ErrorResponse of(ErrorCode errorCode, String customMessage) {
        return new ErrorResponse(errorCode, customMessage);
    }

    /**
     * 팩토리 메서드 3: HttpStatus + 메시지로 에러 응답 생성
     * ErrorCode에 정의되지 않은 예외 상황에 사용
     *
     * 예: ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "예상치 못한 에러")
     *
     * httpStatus.value(): 숫자 코드를 가져옴 (예: 500)
     * httpStatus.name(): 이름을 가져옴 (예: "INTERNAL_SERVER_ERROR")
     */
    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return new ErrorResponse(httpStatus.value(), httpStatus.name(), message);
    }

    /**
     * 팩토리 메서드 4: ErrorCode + BindingResult로 에러 응답 생성
     * @Valid 검증 실패 시, 어떤 필드가 어떤 이유로 실패했는지 상세 정보를 포함
     *
     * BindingResult: 스프링이 입력값 검증 결과를 담아주는 객체
     * 예: 이메일 필드가 빈 값이면 → {field: "email", message: "이메일을 입력해주세요"}
     */
    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        // BindingResult에서 필드별 에러 정보를 추출해서 ValidationError 리스트로 변환
        return new ErrorResponse(errorCode, ValidationError.from(bindingResult));
    }

    /**
     * ValidationError: 필드별 검증 에러 정보를 담는 내부 클래스
     *
     * 내부 클래스(inner class)란?
     * → 클래스 안에 정의된 클래스. ErrorResponse와 밀접하게 관련된 보조 클래스
     * static: 외부 클래스의 인스턴스 없이도 사용 가능
     *
     * JSON 변환 시 이런 형태가 됨:
     * { "field": "email", "message": "이메일 형식이 올바르지 않습니다" }
     */
    @Getter
    public static class ValidationError {

        // 어떤 필드에서 에러가 발생했는지 (예: "email", "password", "name")
        private final String field;

        // 해당 필드의 에러 메시지 (예: "이메일 형식이 올바르지 않습니다")
        private final String message;

        // private 생성자: from() 메서드를 통해서만 생성
        private ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        /**
         * BindingResult에서 필드 에러 목록을 추출해서 ValidationError 리스트로 변환
         *
         * Stream API를 사용함:
         * Stream이란? 데이터를 하나씩 꺼내서 가공하는 파이프라인 방식
         * 파이썬의 리스트 컴프리헨션과 비슷한 개념
         *
         * 흐름:
         * 1. bindingResult.getFieldErrors() → 필드 에러 목록을 가져옴
         * 2. .stream() → 스트림(흐름)으로 변환
         * 3. .map() → 각 에러를 ValidationError 객체로 변환
         * 4. .collect(Collectors.toList()) → 결과를 List로 모음
         */
        private static List<ValidationError> from(BindingResult bindingResult) {
            return bindingResult.getFieldErrors()  // 필드 에러 목록 가져오기
                    .stream()                       // 스트림으로 변환 (하나씩 처리하기 위해)
                    .map(error -> new ValidationError(  // 각 에러를 ValidationError로 변환
                            error.getField(),           // 에러가 발생한 필드 이름 (예: "email")
                            error.getDefaultMessage()   // 에러 메시지 (예: "이메일을 입력해주세요")
                    ))
                    .collect(Collectors.toList());   // 변환된 결과를 List로 모아서 반환
        }
    }
}