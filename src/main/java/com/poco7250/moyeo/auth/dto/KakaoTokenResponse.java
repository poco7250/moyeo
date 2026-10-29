package com.poco7250.moyeo.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 토큰 요청(kauth.kakao.com/oauth/token) 응답. 필요한 필드만 매핑한다.
 *
 * @param accessToken 카카오 액세스 토큰(로그·toString에 노출 금지)
 * @param tokenType   토큰 타입(bearer)
 * @param expiresIn   액세스 토큰 만료까지 남은 초
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTokenResponse(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("token_type") String tokenType,
		@JsonProperty("expires_in") Long expiresIn) {

	/**
	 * 액세스 토큰을 가린 문자열을 돌려준다.
	 */
	@Override
	public String toString() {
		return "KakaoTokenResponse[accessToken=****, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
	}
}
