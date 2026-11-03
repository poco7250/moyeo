package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.place.client.KakaoLocalClient;
import com.poco7250.moyeo.place.config.PlaceSearchProperties;
import com.poco7250.moyeo.place.dto.PlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 검색 서비스. 카카오 로컬 API를 매 요청마다 실시간 프록시하며 결과를 DB·캐시에 남기지 않는다
 * (PRD 4.3 장소 도메인 규칙). 그래서 트랜잭션·Repository가 없다.
 */
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

	private final KakaoLocalClient kakaoLocalClient;
	private final PlaceSearchRateLimiter rateLimiter;
	private final PlaceSearchProperties properties;

	/**
	 * 회원별 빈도 제한을 확인한 뒤 카카오에 검색해 결과를 그대로 돌려준다.
	 *
	 * @param memberId 검색하는 회원
	 * @param query    검색어(컨트롤러에서 공백·길이 검증 완료)
	 * @param page     페이지(1부터)
	 */
	public PlaceSearchResponse search(Long memberId, String query, int page) {
		rateLimiter.acquire(memberId);
		return PlaceSearchResponse.of(kakaoLocalClient.searchKeyword(query.strip(), page, properties.pageSize()), page);
	}
}
