package com.poco7250.moyeo.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 사용자 정보 조회(kapi.kakao.com/v2/user/me) 응답. 필요한 필드만 매핑한다.
 *
 * @param id           카카오 회원 번호
 * @param kakaoAccount 카카오계정 정보(동의 항목에 따라 비어 있을 수 있음)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(
		Long id,
		@JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

	/**
	 * 서비스에서 쓰는 사용자 정보로 변환한다. 계정·프로필 정보가 없으면 해당 필드는 null이다.
	 */
	public KakaoUserInfo toUserInfo() {
		if (kakaoAccount == null) {
			return new KakaoUserInfo(id, null, null, null);
		}
		Profile profile = kakaoAccount.profile();
		String nickname = profile == null ? null : profile.nickname();
		String profileImageUrl = profile == null ? null : profile.profileImageUrl();
		return new KakaoUserInfo(id, nickname, profileImageUrl, kakaoAccount.email());
	}

	/**
	 * 카카오계정 정보.
	 *
	 * @param email   이메일(미동의 시 null)
	 * @param profile 프로필(미동의 시 null)
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record KakaoAccount(String email, Profile profile) {
	}

	/**
	 * 카카오 프로필.
	 *
	 * @param nickname        닉네임
	 * @param profileImageUrl 프로필 이미지 URL
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Profile(
			String nickname,
			@JsonProperty("profile_image_url") String profileImageUrl) {
	}
}
