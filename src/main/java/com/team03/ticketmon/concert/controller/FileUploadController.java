package com.team03.ticketmon.concert.controller;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.team03.ticketmon._global.service.UrlConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.exception.StorageUploadException;
import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon._global.util.FileUtil;
import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.StoragePathProvider;
import com.team03.ticketmon._global.util.uploader.StorageUploader; // 🔄 인터페이스 의존
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.SellerConcertRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FileUploadController — 콘서트 포스터 이미지 업로드/삭제/복구 API
 *
 * 이 클래스가 하는 일:
 *   1. 포스터 이미지 업로드 후 원본 URL을 CloudFront URL로 변환하여 Concert 엔티티에 저장
 *   2. 콘서트 단위(포스터 전체 삭제) 또는 특정 파일 URL 단위의 삭제 처리
 *   3. 판매자 권한 확인 후 임시 파일 삭제 및 원본 포스터 복구 기능 제공
 *
 * 동작 흐름:
 *   - 업로드: 파일 검증 → 버킷/경로 생성 → StorageUploader 업로드 → CloudFront URL 변환
 *     → concertId가 있으면 DB 저장 → 실패 시 업로드 파일 롤백
 *   - 삭제: 판매자 권한 검증 → 스토리지 삭제 → DB의 posterImageUrl을 null 처리
 *   - 복구: 클라이언트가 넘긴 원본 URL을 CloudFront URL로 변환 후 DB에만 반영(스토리지 무변경)
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

	private final StorageUploader storageUploader; // Spring이 @Profile에 따라 자동 주입
	private final SellerConcertRepository sellerConcertRepository;
	private final ConcertRepository concertRepository;
	private final StoragePathProvider storagePathProvider;
	private final UrlConversionService urlConversionService;

	/**
	 * ✅ 고유 파일명 생성 (환경별 폴더 구조 고려)
	 * @param concertId 콘서트 ID
	 * @param originalFilename 원본 파일명
	 * @return 고유 파일명
	 */
	private String generateUniqueFilename(Long concertId, String originalFilename, String contentType) {

		String extension = FileUtil.getExtensionFromMimeType(contentType);
		// 콘서트 ID가 있으면 포스터 경로, 없으면 임시 경로 사용
		if (concertId != null) {
			return storagePathProvider.getPosterPath(concertId, extension);
		} else {
			// 등록 모드: 임시 ID로 정규 경로 생성 (나중에 실제 ID로 교체)
			Long tempId = System.currentTimeMillis(); // 임시 고유 ID
			return storagePathProvider.getPosterPath(tempId, extension);
		}
	}

	@PostMapping("/poster")
	@Transactional
	public ResponseEntity<SuccessResponse<String>> uploadPoster(
		@RequestParam("file") MultipartFile file,
		@RequestParam(required = false) Long concertId
	) {
		String uploadedUrl = null;
		try {
			FileValidator.validate(file);

			String bucket = storagePathProvider.getPosterBucketName(); // StoragePathProvider에서 버킷 이름 가져오기
			String uniquePath = generateUniqueFilename(concertId, file.getOriginalFilename(), file.getContentType());
			log.info("📁 고유 파일명 생성: {}", uniquePath);

			uploadedUrl = storageUploader.uploadFile(file, bucket, uniquePath); // 버킷 이름 전달

			String cloudFrontUrl = urlConversionService.convertToCloudFrontUrl(uploadedUrl);
			log.info("✅ 파일 업로드 성공 - 원본 URL: {}, CloudFront URL: {}", uploadedUrl, cloudFrontUrl);

			// concertId가 있으면 DB에 URL 저장
			if (concertId != null) {
				log.info("🔍 DB에 포스터 URL 저장 시작 - concertId: {}", concertId);

				Optional<Concert> concertOpt = concertRepository.findById(concertId);
				if (concertOpt.isEmpty()) {
					log.error("❌ 존재하지 않는 콘서트 - concertId: {}", concertId);
					// 업로드된 파일 삭제 (롤백)
					try {
						storageUploader.deleteFile(bucket, uploadedUrl);
						log.info("🔄 업로드 롤백 완료");
					} catch (Exception rollbackEx) {
						log.error("❌ 업로드 롤백 실패", rollbackEx);
					}
					throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
				}

				Concert concert = concertOpt.get();
				concert.setPosterImageUrl(cloudFrontUrl);
				concertRepository.save(concert);

				log.info("✅ DB에 포스터 URL 저장 완료 - concertId: {}, URL: {}", concertId, cloudFrontUrl);
			}

			return ResponseEntity.ok(SuccessResponse.of("파일 업로드 성공", cloudFrontUrl));

		} catch (BusinessException e) {
			if (uploadedUrl != null) {
				rollbackUploadedFile(uploadedUrl);
			}
			throw e;
		} catch (Exception e) {
			if (uploadedUrl != null) {
				rollbackUploadedFile(uploadedUrl);
			}
			throw new StorageUploadException("포스터 업로드 실패", e);
		}
	}

	/**
	 * 🔄 업로드된 파일 롤백 (환경 무관 - 인터페이스 호출 방식 동일)
	 * 버킷명은 각 환경의 실제 버킷명 사용 필요
	 */
	private void rollbackUploadedFile(String uploadedUrl) {
		try {
			String bucket = storagePathProvider.getPosterBucketName(); // StoragePathProvider에서 버킷 이름 가져오기
			storageUploader.deleteFile(bucket, uploadedUrl);
			log.info("🔄 업로드 실패로 인한 파일 롤백 완료 - URL: {}", uploadedUrl);
		} catch (Exception rollbackException) {
			log.error("❌ 파일 롤백 실패 - URL: {}", uploadedUrl, rollbackException);
		}
	}

	@DeleteMapping("/poster/{concertId}")
	@Transactional
	public ResponseEntity<?> deletePosterByConcert(
		@PathVariable Long concertId,
		@RequestParam Long sellerId
	) {
		try {
			log.info("🗑️ 콘서트 포스터 삭제 요청 - concertId: {}, sellerId: {}", concertId, sellerId);

			log.info("🔍 단계 1: 권한 검증 시작");
			if (!sellerConcertRepository.existsByConcertIdAndSellerId(concertId, sellerId)) {
				log.warn("❌ 권한 검증 실패 - concertId: {}, sellerId: {}", concertId, sellerId);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of(
						"success", false,
						"message", "해당 콘서트의 포스터를 삭제할 권한이 없습니다."
					));
			}
			log.info("✅ 단계 1: 권한 검증 완료");

			log.info("🔍 단계 2: 콘서트 조회 시작");
			Optional<Concert> concertOpt = concertRepository.findById(concertId);
			if (concertOpt.isEmpty()) {
				log.warn("❌ 콘서트를 찾을 수 없음 - concertId: {}", concertId);
				return ResponseEntity.notFound().build();
			}
			log.info("✅ 단계 2: 콘서트 조회 완료");

			Concert concert = concertOpt.get();
			log.info("🔍 단계 3: 포스터 URL 확인 시작");
			String currentPosterUrl = concert.getPosterImageUrl();
			log.info("🔍 현재 포스터 URL: [{}]", currentPosterUrl);

			if (currentPosterUrl == null || currentPosterUrl.trim().isEmpty()) {
				log.info("ℹ️ 삭제할 포스터가 없음 - concertId: {}", concertId);
				return ResponseEntity.ok(Map.of(
					"success", true,
					"message", "삭제할 포스터 이미지가 없습니다.",
					"alreadyEmpty", true
				));
			}
			log.info("✅ 단계 3: 포스터 URL 확인 완료 - URL 존재함");

			log.info("🔍 단계 4: 스토리지 파일 삭제 시작");
			try {
				String bucket = storagePathProvider.getPosterBucketName(); // StoragePathProvider에서 버킷 이름 가져오기
				storageUploader.deleteFile(bucket, currentPosterUrl);
				log.info("✅ 스토리지 파일 삭제 완료 - URL: {}", currentPosterUrl);
			} catch (Exception storageException) {
				log.warn("⚠️ 스토리지 파일 삭제 실패 (계속 진행) - URL: {}", currentPosterUrl, storageException);
			}
			log.info("✅ 단계 4: 스토리지 파일 삭제 처리 완료");

			// 4. DB에서 poster_image_url 필드 null로 업데이트
			log.info("🔍 단계 5: DB 업데이트 시작");
			int updatedRows = sellerConcertRepository.updatePosterImageUrl(concertId, sellerId, null);
			log.info("🔍 DB 업데이트 결과: {} rows affected", updatedRows);

			if (updatedRows == 0) {
				log.warn("⚠️ DB 업데이트 실패 - 권한 없음 또는 존재하지 않는 콘서트: concertId={}, sellerId={}", concertId, sellerId);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of(
						"success", false,
						"message", "포스터 삭제 권한이 없거나 존재하지 않는 콘서트입니다."
					));
			}
			log.info("✅ 단계 5: DB 업데이트 완료");

			log.info("✅ 포스터 삭제 완료 - concertId: {}", concertId);

			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "콘서트 포스터가 삭제되었습니다.",
				"deletedUrl", currentPosterUrl,
				"concertId", concertId
			));

		} catch (BusinessException e) {
			log.error("❌ 비즈니스 로직 오류 - concertId: {}", concertId, e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of(
					"success", false,
					"message", e.getMessage()
				));

		} catch (Exception e) {
			log.error("❌ 콘서트 포스터 삭제 실패 - concertId: {}", concertId, e);

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of(
					"success", false,
					"message", "포스터 삭제 중 오류가 발생했습니다: " + e.getMessage()
				));
		}
	}

	@DeleteMapping("/poster/specific")
	@Transactional
	public ResponseEntity<?> deleteSpecificFile(
		@RequestParam String fileUrl,
		@RequestParam Long concertId,
		@RequestParam Long sellerId
	) {
		try {
			log.info("🗑️ 특정 파일 삭제 요청 - fileUrl: {}, concertId: {}, sellerId: {}",
				fileUrl, concertId, sellerId);

			// 권한 검증
			if (!sellerConcertRepository.existsByConcertIdAndSellerId(concertId, sellerId)) {
				log.warn("❌ 권한 검증 실패 - concertId: {}, sellerId: {}", concertId, sellerId);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of(
						"success", false,
						"message", "해당 파일을 삭제할 권한이 없습니다."
					));
			}

			// 스토리지에서 특정 파일 삭제 (환경별 자동 처리)
			try {
				String bucket = storagePathProvider.getPosterBucketName(); // StoragePathProvider에서 버킷 이름 가져오기
				storageUploader.deleteFile(bucket, fileUrl);
				log.info("✅ 스토리지 특정 파일 삭제 완료 - URL: {}", fileUrl);
			} catch (Exception storageException) {
				log.warn("⚠️ 스토리지 특정 파일 삭제 실패 - URL: {}", fileUrl, storageException);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of(
						"success", false,
						"message", "파일 삭제에 실패했습니다."
					));
			}

			// 2. DB에서 posterImageUrl 필드를 null로 업데이트
			log.info("🔍 단계 3: DB에서 포스터 URL 제거 시작");
			int updatedRows = sellerConcertRepository.updatePosterImageUrl(concertId, sellerId, null);
			log.info("🔍 DB 업데이트 결과: {} rows affected", updatedRows);

			if (updatedRows == 0) {
				log.warn("⚠️ DB 업데이트 실패 - 권한 없음 또는 존재하지 않는 콘서트: concertId={}, sellerId={}",
						concertId, sellerId);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
						.body(Map.of(
								"success", false,
								"message", "DB 업데이트 권한이 없거나 존재하지 않는 콘서트입니다."
						));
			}
			log.info("✅ 단계 3: DB 업데이트 완료");

			log.info("✅ 특정 파일 삭제 및 DB 업데이트 완료 - concertId: {}", concertId);

			return ResponseEntity.ok(Map.of(
					"success", true,
					"message", "파일이 삭제되고 DB가 업데이트되었습니다.",
					"deletedUrl", fileUrl,
					"concertId", concertId
			));

		} catch (Exception e) {
			log.error("❌ 특정 파일 삭제 실패", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of(
							"success", false,
							"message", "파일 삭제 중 오류가 발생했습니다: " + e.getMessage()
					));
		}
	}

	@DeleteMapping("/temp")
	@Transactional
	public ResponseEntity<?> deleteTempFile(
		@RequestParam String fileUrl,
		@RequestParam Long sellerId
	) {
		try {
			log.info("🗑️ 임시 파일 삭제 요청 - fileUrl: {}, sellerId: {}", fileUrl, sellerId);

			// 임시 파일은 특별한 권한 검증 없이 삭제 (sellerId만 확인)
			if (sellerId == null || sellerId <= 0) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of(
						"success", false,
						"message", "유효하지 않은 사용자입니다."
					));
			}

			// 스토리지에서 임시 파일 삭제 (환경별 자동 처리)
			try {
				String bucket = storagePathProvider.getPosterBucketName();
				storageUploader.deleteFile(bucket, fileUrl);
				log.info("✅ 스토리지 임시 파일 삭제 완료 - URL: {}", fileUrl);
			} catch (Exception storageException) {
				log.warn("⚠️ 스토리지 임시 파일 삭제 실패 - URL: {}", fileUrl, storageException);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of(
						"success", false,
						"message", "임시 파일 삭제에 실패했습니다."
					));
			}

			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "임시 파일이 삭제되었습니다.",
				"deletedUrl", fileUrl
			));

		} catch (Exception e) {
			log.error("❌ 임시 파일 삭제 실패", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of(
					"success", false,
					"message", "임시 파일 삭제 중 오류가 발생했습니다: " + e.getMessage()
				));
		}
	}

	@PatchMapping("/poster/{concertId}/restore")
	@Transactional
	public ResponseEntity<?> restoreOriginalPoster(
		@PathVariable Long concertId,
		@RequestParam Long sellerId,
		@RequestBody Map<String, String> request
	) {
		try {
			String originalUrl = request.get("originalUrl");
			log.info("🔄 원본 포스터 복구 요청 - concertId: {}, sellerId: {}, originalUrl: {}",
				concertId, sellerId, originalUrl);

			if (originalUrl == null || originalUrl.trim().isEmpty()) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of(
						"success", false,
						"message", "복구할 원본 URL이 필요합니다."
					));
			}

			if (!sellerConcertRepository.existsByConcertIdAndSellerId(concertId, sellerId)) {
				log.warn("❌ 권한 검증 실패 - concertId: {}, sellerId: {}", concertId, sellerId);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of(
						"success", false,
						"message", "해당 콘서트를 수정할 권한이 없습니다."
					));
			}

			String cloudFrontUrl = urlConversionService.convertToCloudFrontUrl(originalUrl);
			log.info("🔄 URL 변환: {} -> {}", originalUrl, cloudFrontUrl);

			// DB에서 원본 URL로 복구 (스토리지는 건드리지 않음)
			int updatedRows = sellerConcertRepository.updatePosterImageUrl(concertId, sellerId, cloudFrontUrl);

			if (updatedRows == 0) {
				log.warn("⚠️ DB 업데이트 실패 - concertId: {}, sellerId: {}", concertId, sellerId);
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of(
						"success", false,
						"message", "콘서트를 찾을 수 없거나 권한이 없습니다."
					));
			}

			log.info("✅ 원본 포스터 복구 완료 - concertId: {}, cloudFrontUrl: {}", concertId, cloudFrontUrl);

			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "원본 포스터로 복구되었습니다.",
				"restoredUrl", cloudFrontUrl,
				"concertId", concertId
			));

		} catch (Exception e) {
			log.error("❌ 원본 포스터 복구 실패 - concertId: {}", concertId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of(
					"success", false,
					"message", "원본 복구 중 오류가 발생했습니다: " + e.getMessage()
				));
		}
	}
}