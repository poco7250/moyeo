package com.poco7250.moyeo.place.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 장소 검색 설정(place.search.*). PRD에 수치가 없어 기본값은 Task 015에서 정했다.
 *
 * @param pageSize  한 페이지 결과 수(카카오 키워드 검색 최대 15)
 * @param rateLimit 회원별 검색 빈도 제한
 */
@ConfigurationProperties(prefix = "place.search")
public record PlaceSearchProperties(
		@DefaultValue("15") int pageSize,
		@DefaultValue RateLimit rateLimit) {

	/**
	 * 회원별 검색 빈도 제한. window 동안 maxRequests번까지 허용한다.
	 *
	 * @param maxRequests 윈도우당 최대 요청 수
	 * @param window      윈도우 길이
	 */
	public record RateLimit(
			@DefaultValue("30") int maxRequests,
			@DefaultValue("1m") Duration window) {
	}
}
