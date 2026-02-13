package com.team03.ticketmon._global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
// Lombok의 @Data
// → @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor
import lombok.Data;

@Data

// @ConfigurationProperties(prefix = "ai.summary.condition")
// → yml 파일에서 "ai.summary.condition" 아래의 값들을 이 클래스 필드에 자동 매핑
// yml의 kebab-case(min-review-count)가 camelCase(minReviewCount)로 자동 변환됨
@ConfigurationProperties(prefix = "ai.summary.condition")

// → AI 요약 기능이 실행되기 위한 "조건(Condition)" 설정값을 담는 클래스
public class AiSummaryConditionProperties {

	// AI 요약을 생성하기 위해 필요한 "최소 리뷰 수"
	// → 기본값: 10 (리뷰가 10개 이상 있어야 AI 요약을 만듦)
	private Integer minReviewCount = 10;

	// "의미 있는 리뷰 수 변화량" (절대값 기준)
	// → 기본값: 3 (리뷰가 3개 이상 새로 추가되면 "의미 있는 변화"로 판단)
	// → AI 요약을 다시 생성(업데이트)할지 결정하는 기준 중 하나
	private Integer significantCountChange = 3;

	// "의미 있는 리뷰 수 변화 비율" (퍼센트 기준)
	// → 기본값: 0.2 (= 20%)
	// → 리뷰 수가 20% 이상 증가하면 "의미 있는 변화"로 판단
	private Double significantCountChangeRatio = 0.2;

	// 리뷰 "내용"이 바뀌었을 때도 요약을 업데이트할지 여부
	// → 기본값: true (내용이 바뀌면 요약도 다시 만듦)
	private Boolean updateOnAnyContentChange = true;

	// 요약 업데이트 사이의 "최대 간격" (시간 단위)
	// → 기본값: 1L (= 1시간)
	// → 아무리 변화가 많아도, 최소 1시간은 기다렸다가 요약을 다시 만듦
	private Long maxUpdateIntervalHours = 1L;
}