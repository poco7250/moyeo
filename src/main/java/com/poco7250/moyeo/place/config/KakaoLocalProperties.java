package com.poco7250.moyeo.place.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카카오 로컬(장소 검색) API 설정(kakao.local.*).
 * REST 키는 application-local.properties나 환경 변수로만 넣고, 클라이언트 응답·로그에 내보내지 않는다.
 *
 * @param restApiKey     카카오 REST API 키(로그·toString에 노출 금지)
 * @param searchUri      키워드 장소 검색 URI
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    읽기 타임아웃
 */
@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(
		String restApiKey,
		@DefaultValue("https://dapi.kakao.com/v2/local/search/keyword.json") String searchUri,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("3s") Duration readTimeout) {

	/**
	 * REST 키를 가린 문자열을 돌려준다(설정 덤프·로그로 키가 새지 않도록).
	 */
	@Override
	public String toString() {
		return "KakaoLocalProperties[restApiKey=****"
				+ ", searchUri=" + searchUri
				+ ", connectTimeout=" + connectTimeout
				+ ", readTimeout=" + readTimeout + "]";
	}
}
