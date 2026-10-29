package com.poco7250.moyeo.auth.dto;

/**
 * 로그인·재발급 결과로 내려줄 토큰 쌍.
 *
 * @param accessToken  Access 토큰(JWT, 로그·toString에 노출 금지)
 * @param refreshToken Refresh 토큰 원문(로그·toString에 노출 금지)
 */
public record TokenPair(String accessToken, String refreshToken) {

	/**
	 * 토큰 원문을 가린 문자열을 돌려준다.
	 */
	@Override
	public String toString() {
		return "TokenPair[accessToken=****, refreshToken=****]";
	}
}
