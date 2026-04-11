package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.ConcertDTO;
import com.team03.ticketmon.concert.dto.ConcertFilterDTO;
import com.team03.ticketmon.concert.dto.ConcertSearchDTO;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.service.UrlConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ConcertService — 콘서트 조회/검색/필터/AI 요약 서비스
 *
 * 이 클래스가 하는 일:
 *   1. 전체/상태별 콘서트 페이징·정렬 조회 (COMPLETED, CANCELLED 제외한 활성 상태만)
 *   2. 키워드/날짜/가격 범위 기반 검색 및 필터링
 *   3. 단건 상세 조회(캐싱), AI 요약 조회, 대기열 활성 여부 조회
 *   4. 콘서트 상태 변경 시 상세/검색 캐시 무효화 지원
 *
 * 동작 흐름:
 *   - 요청 → 파라미터/정렬/페이징 검증 → ConcertRepository 호출
 *     → Entity → DTO 변환(포스터 URL은 CloudFront URL로 변환)
 *     → 조회 캐시에 반영
 *   - 관리자/배치 로직에서는 getConcertEntityById()로 엔티티 직접 접근
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {

	private final ConcertRepository concertRepository;
	private final ConcertSeatRepository concertSeatRepository;
	private final UrlConversionService urlConversionService;

	// COMPLETED, CANCELLED 제외한 활성 상태만
	private static final List<ConcertStatus> ACTIVE_STATUSES = Arrays.asList(
		ConcertStatus.SCHEDULED,
		ConcertStatus.ON_SALE,
		ConcertStatus.BOOKING_CLOSED,
		ConcertStatus.SOLD_OUT  // SOLD_OUT은 포함 (매진이지만 여전히 정보 확인 가능)
	);

	// 페이징 관련 상수
	private static final int MIN_PAGE = 0;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 100;
	private static final int DEFAULT_SIZE = 20;

	/**
	 * 전체 콘서트 조회 (페이징 + 정렬) - COMPLETED/CANCELLED 제외
	 */
	public Page<ConcertDTO> getAllConcerts(int page, int size, String sortBy, String sortDir) {
		// 페이징 파라미터 검증
		validatePagingParameters(page, size);

		// 정렬 파라미터 검증 및 처리
		validateSortParameters(sortBy, sortDir);

		// Sort 객체 생성
		Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
			? Sort.Direction.DESC
			: Sort.Direction.ASC;

		Sort sort = Sort.by(direction, mapSortField(sortBy));
		Pageable pageable = PageRequest.of(page, size, sort);

		log.info("🔍 콘서트 목록 조회 - page: {}, size: {}, sortBy: {}, sortDir: {}",
			page, size, sortBy, sortDir);

		// 정렬이 적용된 페이징으로 활성 콘서트 조회
		return concertRepository.findActiveConcerts(pageable)
			.map(this::convertToDTO);
	}

	/**
	 * 정렬 필드 매핑 (DTO 필드명 → Entity 필드명)
	 */
	private String mapSortField(String sortBy) {
		switch (sortBy) {
			case "concertDate":
				return "concertDate";
			case "title":
				return "title";
			case "artist":
				return "artist";
			case "createdAt":
				return "createdAt";
			default:
				return "concertDate"; // 기본값
		}
	}

	/**
	 * 정렬 파라미터 검증
	 */
	private void validateSortParameters(String sortBy, String sortDir) {
		List<String> allowedSortFields = Arrays.asList("concertDate", "title", "artist", "createdAt");
		if (!allowedSortFields.contains(sortBy)) {
			throw new BusinessException(ErrorCode.INVALID_SORT_FIELD);
		}

		List<String> allowedSortDirections = Arrays.asList("asc", "desc");
		if (!allowedSortDirections.contains(sortDir.toLowerCase())) {
			throw new BusinessException(ErrorCode.INVALID_SORT_DIRECTION);
		}
	}

	/**
	 * 기존 getAllConcerts 메서드 (하위 호환성 유지)
	 */
	public Page<ConcertDTO> getAllConcerts(int page, int size) {
		return getAllConcerts(page, size, "concertDate", "asc");
	}

	/**
	 * 전체 콘서트 조회 (페이징 없음) - COMPLETED/CANCELLED 제외
	 */
	public List<ConcertDTO> getAllConcertsWithoutPaging() {
		// 활성 콘서트만 조회
		return concertRepository.findActiveConcerts()
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 상태별 콘서트 조회 (페이징) - 특정 상태 조회용 (관리자 등)
	 */
	public Page<ConcertDTO> getConcertsWithPaging(ConcertStatus status, Pageable pageable) {
		if (status == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (pageable == null) {
			throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
		}
		Page<Concert> concertPage = concertRepository
			.findByStatusOrderByConcertDateAsc(status, pageable);
		return concertPage.map(this::convertToDTO);
	}

	/**
	 * 키워드로 콘서트 검색 - COMPLETED/CANCELLED 제외
	 */
	@Cacheable(value = "searchResults", key = "#keyword")
	public List<ConcertDTO> searchByKeyword(@Param("keyword") String keyword) {
		log.info("🔍 [CACHE MISS] searchByKeyword 실행 - keyword: '{}' (DB 조회, COMPLETED/CANCELLED 제외)", keyword);
		validateKeyword(keyword);

		List<ConcertDTO> results = concertRepository
			.findByKeyword(keyword.trim())
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());

		log.info("✅ [DB 조회 완료] 활성 콘서트 검색 결과 수: {}, keyword: '{}'", results.size(), keyword);
		return results;
	}

	/**
	 * 날짜 범위로 콘서트 필터링 - COMPLETED/CANCELLED 제외
	 */
	public List<ConcertDTO> filterByDateRange(LocalDate startDate, LocalDate endDate) {
		validateDateRange(startDate, endDate);

		// Repository에서 이미 COMPLETED/CANCELLED 제외
		return concertRepository
			.findByDateRange(startDate, endDate)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 가격 범위로 콘서트 필터링 - COMPLETED/CANCELLED 제외
	 */
	public List<ConcertDTO> filterByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
		validatePriceRange(minPrice, maxPrice);

		// Repository에서 이미 COMPLETED/CANCELLED 제외
		return concertRepository
			.findByPriceRange(minPrice, maxPrice)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 날짜와 가격 범위로 콘서트 필터링 - COMPLETED/CANCELLED 제외
	 */
	public List<ConcertDTO> filterByDateAndPriceRange(
		LocalDate startDate, LocalDate endDate,
		BigDecimal minPrice, BigDecimal maxPrice) {

		validateDateRange(startDate, endDate);
		validatePriceRange(minPrice, maxPrice);

		// Repository에서 이미 COMPLETED/CANCELLED 제외
		return concertRepository
			.findByDateAndPriceRange(startDate, endDate, minPrice, maxPrice)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 필터 조건 적용 - COMPLETED/CANCELLED 제외
	 */
	public List<ConcertDTO> applyFilters(ConcertFilterDTO filterDTO) {
		if (filterDTO == null) {
			// 활성 콘서트만 반환
			return getAllConcertsWithoutPaging();
		}

		LocalDate startDate = filterDTO.getStartDate();
		LocalDate endDate = filterDTO.getEndDate();
		BigDecimal priceMin = filterDTO.getPriceMin();
		BigDecimal priceMax = filterDTO.getPriceMax();

		boolean hasDateFilter = startDate != null || endDate != null;
		boolean hasPriceFilter = priceMin != null || priceMax != null;

		if (hasDateFilter && hasPriceFilter) {
			return filterByDateAndPriceRange(startDate, endDate, priceMin, priceMax);
		} else if (hasDateFilter) {
			return filterByDateRange(startDate, endDate);
		} else if (hasPriceFilter) {
			return filterByPriceRange(priceMin, priceMax);
		} else {
			// 활성 콘서트만 반환
			return getAllConcertsWithoutPaging();
		}
	}

	/**
	 * 날짜별 콘서트 조회 - ON_SALE 상태만 (기존 로직 유지)
	 */
	public List<ConcertDTO> getConcertsByDate(LocalDate concertDate) {
		if (concertDate == null) {
			throw new BusinessException(ErrorCode.CONCERT_DATE_REQUIRED);
		}

		return concertRepository
			.findByConcertDateAndStatusOrderByConcertDateAsc(concertDate, ConcertStatus.ON_SALE)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 예매 가능한 콘서트 조회 (기존 로직 유지)
	 */
	public List<ConcertDTO> getBookableConcerts() {
		return concertRepository
			.findBookableConcerts()
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 콘서트 검색 (DTO 기반) - COMPLETED/CANCELLED 제외
	 */
	public List<ConcertDTO> searchConcerts(ConcertSearchDTO searchDTO) {
		if (searchDTO == null || searchDTO.getKeyword() == null) {
			throw new BusinessException(ErrorCode.SEARCH_CONDITION_REQUIRED);
		}

		// 자동으로 COMPLETED/CANCELLED 제외됨
		return searchByKeyword(searchDTO.getKeyword());
	}

	/**
	 * ID로 콘서트 조회 - 특정 ID 조회는 상태 무관하게 조회 (상세 페이지용)
	 */
	@Cacheable(value = "concertDetail", key = "#concertId")
	public Optional<ConcertDTO> getConcertById(@Param("concertId") Long concertId) {

		Optional<Concert> concert = concertRepository.findById(concertId);
		Optional<ConcertDTO> result = concert.map(this::convertToDTO);

		log.info("✅ [DB 조회 완료] concertId: {}, 결과: {}",
			concertId, result.isPresent() ? "찾음" : "없음");

		return result;
	}

	/**
	 * AI 요약 정보 조회 - 상태 무관하게 조회 가능
	 */
	public String getAiSummary(Long id) {
		validateConcertId(id);

		return concertRepository.findById(id)
			.map(Concert::getAiSummary)
			.filter(summary -> summary != null && !summary.trim().isEmpty())
			.orElse("AI 요약 정보가 아직 생성되지 않았습니다.");
	}

	/**
	 * 🛠️ 관리자용: 콘서트 엔티티 직접 조회 (내부 처리용)
	 *
	 * AI 요약 생성 등 엔티티가 필요한 관리 작업에서 사용
	 * 일반 사용자용 getConcertById()와 구분하여 명명
	 *
	 * @param id 콘서트 ID
	 * @return Concert 엔티티 (Optional)
	 */
	public Optional<Concert> getConcertEntityById(Long id) {
		validateConcertId(id);
		return concertRepository.findById(id);
	}

	/**
	 * 콘서트 상세 캐시 무효화 + 검색 캐시 무효화
	 * 콘서트 상태가 변경될 때 (예: ON_SALE → COMPLETED) 호출하여
	 * 목록에서 해당 콘서트가 사라지도록 캐시 갱신
	 */
	@CacheEvict(value = {"concertDetail", "searchResults"}, key = "#concertId")
	public void evictConcertDetailCache(Long concertId) {
		log.info("🗑️ [CACHE EVICT] 콘서트 상세 캐시 무효화 - concertId: {}", concertId);
	}

	/**
	 * 검색 결과 캐시 전체 무효화
	 * 콘서트 상태 대량 변경 시 (스케줄러 등) 호출
	 */
	@CacheEvict(value = "searchResults", allEntries = true)
	public void evictSearchCache() {
		log.info("🗑️ [CACHE EVICT] 검색 결과 캐시 전체 무효화 (COMPLETED/CANCELLED 처리 반영)");
	}

	/**
	 * 페이징 파라미터 검증
	 */
	private void validatePagingParameters(int page, int size) {
		if (page < MIN_PAGE) {
			throw new BusinessException(ErrorCode.INVALID_PAGE_NUMBER);
		}
		if (size < MIN_SIZE || size > MAX_SIZE) {
			throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
		}
	}

	/**
	 * 키워드 검증
	 */
	private void validateKeyword(String keyword) {
		if (keyword == null || keyword.trim().isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_SEARCH_KEYWORD);
		}
	}

	/**
	 * 날짜 범위 검증
	 */
	private void validateDateRange(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_DATE_ORDER);
		}
	}

	/**
	 * 가격 범위 검증
	 */
	private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
		if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
		if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
	}

	/**
	 * 콘서트 ID 검증
	 */
	private void validateConcertId(Long id) {
		if (id == null || id <= 0) {
			throw new BusinessException(ErrorCode.INVALID_CONCERT_ID);
		}
	}

	/**
	 * Entity를 DTO로 변환
	 */
	private ConcertDTO convertToDTO(Concert concert) {
		String convertedPosterUrl = urlConversionService.convertToCloudFrontUrl(concert.getPosterImageUrl());
		return new ConcertDTO(
			concert.getConcertId(),
			concert.getTitle(),
			concert.getArtist(),
			concert.getDescription(),
			concert.getSellerId(),
			concert.getVenueName(),
			concert.getVenueAddress(),
			concert.getVenueCapacityType(),
			concert.getConcertDate(),
			concert.getStartTime(),
			concert.getEndTime(),
			concert.getTotalSeats(),
			concert.getBookingStartDate(),
			concert.getBookingEndDate(),
			concert.getMinAge(),
			concert.getMaxTicketsPerUser(),
			concert.getStatus(),
			convertedPosterUrl,
			concert.getAiSummary(),
			concert.getCreatedAt(),
			concert.getUpdatedAt()
		);
	}

	@Cacheable(value = "concertQueueStatus", key = "#concertId")
	public boolean isQueueActive(Long concertId) {
		log.info("Cache miss! DB에서 concertId {}의 대기열 상태를 조회합니다.", concertId);
		return concertRepository.findById(concertId)
			.map(Concert::isQueueActive) // 위에서 추가한 편의 메서드 사용
			.orElse(false); // 콘서트가 없으면 비활성
	}
}