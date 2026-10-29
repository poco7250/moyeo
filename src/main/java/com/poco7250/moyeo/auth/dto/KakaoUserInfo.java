package com.poco7250.moyeo.auth.dto;

import com.poco7250.moyeo.member.dto.KakaoProfile;

/**
 * 카카오에서 받아온 사용자 정보. 사용자가 동의하지 않은 항목은 null이다.
 *
 * @param kakaoId         카카오 회원 번호
 * @param nickname        카카오 닉네임(미동의 시 null)
 * @param profileImageUrl 프로필 이미지 URL(미동의 시 null)
 * @param email           이메일(미동의 시 null)
 */
public record KakaoUserInfo(Long kakaoId, String nickname, String profileImageUrl, String email) {

	/**
	 * 회원 조회·생성용 member DTO로 변환한다(member가 auth를 참조하지 않도록 변환은 auth 쪽에서 한다).
	 */
	public KakaoProfile toProfile() {
		return new KakaoProfile(kakaoId, nickname, profileImageUrl, email);
	}
}
