package com.poco7250.moyeo.place.client;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.place.config.KakaoLocalProperties;
import com.poco7250.moyeo.place.dto.KakaoLocalSearchResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로컬 키워드 검색 클라이언트. 매 요청마다 실시간으로 호출하고 결과를 캐싱·저장하지 않는다.
 * REST 키와 검색어는 로그에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoLocalClient {

	private final RestClient kakaoLocalRestClient;
	private final KakaoLocalProperties properties;

	/**
	 * 키워드로 장소를 검색한다.
	 *
	 * @param query 검색어
	 * @param page  페이지(1부터)
	 * @param size  페이지 크기
	 * @throws BusinessException 키 오류·쿼터 초과·카카오 장애·타임아웃이면 PLACE_SEARCH_UNAVAILABLE
	 */
	public KakaoLocalSearchResponse searchKeyword(String query, int page, int size) {
		try {
			KakaoLocalSearchResponse response = kakaoLocalRestClient.get()
					.uri(buildSearchUri(query, page, size))
					.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, res) -> handleError(res.getStatusCode()))
					.body(KakaoLocalSearchResponse.class);
			if (response == null) {
				log.warn("카카오 로컬 검색 응답 본문이 없음");
				throw new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
			}
			return response;
		} catch (RestClientException e) {
			// 타임아웃·연결 실패·응답 해석 실패. 메시지에 요청 URL(검색어)이 섞일 수 있어 예외 타입만 남긴다
			log.warn("카카오 로컬 검색 호출 실패: {}", e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
		}
	}

	/**
	 * 검색어를 UTF-8로 인코딩한 검색 URI를 만든다.
	 */
	private URI buildSearchUri(String query, int page, int size) {
		return UriComponentsBuilder.fromUriString(properties.searchUri())
				.queryParam("query", query)
				.queryParam("page", page)
				.queryParam("size", size)
				.encode()
				.build()
				.toUri();
	}

	/**
	 * 카카오 에러 응답(401 키 오류, 429 쿼터 초과, 5xx 장애 등)을 모두 검색 불가로 바꾼다.
	 */
	private void handleError(HttpStatusCode status) {
		log.warn("카카오 로컬 검색 실패: status={}", status.value());
		throw new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
	}
}
