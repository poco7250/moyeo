package com.poco7250.moyeo.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카카오 OAuth 설정(kakao.oauth.*).
 * clientId·clientSecret·redirectUri는 application-local.properties나 환경 변수로만 넣는다.
 *
 * @param clientId       카카오 REST API 키
 * @param clientSecret   클라이언트 시크릿(로그·toString에 노출 금지)
 * @param redirectUri    카카오에 등록한 콜백 URI
 * @param tokenUri       토큰 요청 URI
 * @param userInfoUri    사용자 정보 조회 URI
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    읽기 타임아웃
 */
@ConfigurationProperties(prefix = "kakao.oauth")
public record KakaoOAuthProperties(
		String clientId,
		String clientSecret,
		String redirectUri,
		@DefaultValue("https://kauth.kakao.com/oauth/token") String tokenUri,
		@DefaultValue("https://kapi.kakao.com/v2/user/me") String userInfoUri,
		@DefaultValue("3s") Duration connectTimeout,
		@DefaultValue("5s") Duration readTimeout) {

	/**
	 * clientSecret을 가린 문자열을 돌려준다(설정 덤프·로그로 시크릿이 새지 않도록).
	 */
	@Override
	public String toString() {
		return "KakaoOAuthProperties[clientId=" + clientId
				+ ", clientSecret=****"
				+ ", redirectUri=" + redirectUri
				+ ", tokenUri=" + tokenUri
				+ ", userInfoUri=" + userInfoUri
				+ ", connectTimeout=" + connectTimeout
				+ ", readTimeout=" + readTimeout + "]";
	}
}
