package com.team03.ticketmon._global.validation;

/**
 * OnReject — Bean Validation 그룹 마커 인터페이스
 *
 * 판매자 신청 반려 등 "거절(Reject)" 시점에만 적용해야 하는 검증 규칙을 구분하기 위한
 * 그룹 마커로 사용됩니다. (예: {@code @NotBlank(groups = OnReject.class)})
 */
public interface OnReject {}