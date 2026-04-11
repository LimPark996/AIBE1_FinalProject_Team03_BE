package com.team03.ticketmon.concert.util;

import java.security.MessageDigest;
import java.util.List;
import org.springframework.stereotype.Component;
import com.team03.ticketmon.concert.domain.Review;

/**
 * ReviewChecksumGenerator — 리뷰 목록의 MD5 체크섬 생성
 * 리뷰 ID 기준 정렬 + 내용/평점 직렬화 후 MD5 해싱하여 내용 변경 감지에 사용한다.
 */
@Component // Spring의 Bean으로 등록하여 의존성 주입 가능하게 함
public class ReviewChecksumGenerator {

	/**
	 * 리뷰 목록을 입력받아 MD5 체크섬을 생성하는 메서드
	 * 리뷰 내용이 변경되었는지 빠르게 감지하기 위해 사용
	 * @param reviews 체크섬을 생성할 리뷰 목록
	 * @return MD5 해시값 (32자리 16진수 문자열)
	 */
	public String generateChecksum(List<Review> reviews) {
		try {
			// 문자열 연결을 위한 StringBuilder 객체 생성
			StringBuilder content = new StringBuilder();

			// 리뷰를 ID 순으로 정렬하여 일관된 체크섬 생성
			// 동일한 리뷰 데이터는 항상 같은 체크섬을 생성하기 위해 정렬 필요
			reviews.stream()
				// null이거나 공백인 리뷰 설명은 제외 (유효한 리뷰만 처리)
				.filter(review -> review.getDescription() != null && !review.getDescription().trim().isEmpty())
				// 리뷰 ID를 기준으로 오름차순 정렬 (일관성 보장)
				.sorted((r1, r2) -> r1.getId().compareTo(r2.getId()))
				// 각 리뷰의 데이터를 문자열로 연결
				.forEach(review -> {
					content.append(review.getId())    // 리뷰 ID 추가
						.append("|")                      // 구분자 추가
						.append(review.getDescription())  // 리뷰 내용 추가
						.append("|")                      // 구분자 추가
						.append(review.getRating())       // 평점 추가
						.append("\n");                    // 줄바꿈으로 리뷰 구분
				});

			// MD5 해시 알고리즘을 사용하여 MessageDigest 인스턴스 생성
			MessageDigest md = MessageDigest.getInstance("MD5");
			// 연결된 문자열을 바이트 배열로 변환하여 해시값 계산
			byte[] digest = md.digest(content.toString().getBytes());
			// 바이트 배열을 16진수 문자열로 변환하기 위한 StringBuilder
			StringBuilder hexString = new StringBuilder();

			// 각 바이트를 16진수 문자열로 변환
			for (byte b : digest) {
				// 바이트를 16진수로 변환 (0xff와 AND 연산으로 음수 방지)
				String hex = Integer.toHexString(0xff & b);
				// 한 자리 16진수인 경우 앞에 0을 붙여 두 자리로 만듦
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}

			// 최종 32자리 16진수 문자열 반환
			return hexString.toString();
		} catch (Exception e) {
			// 예외 발생 시 RuntimeException으로 감싸서 던짐
			throw new RuntimeException("체크섬 생성 실패", e);
		}
	}
}