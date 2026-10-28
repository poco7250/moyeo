package com.poco7250.moyeo.member.dto;

import com.poco7250.moyeo.member.domain.Member;

/**
 * 외부로 내보내는 회원 정보. kakaoId·이메일은 포함하지 않는다.
 */
public record MemberResponse(Long id, String nickname, String profileImageUrl) {

	/**
	 * 엔티티를 응답 DTO로 변환한다.
	 */
	public static MemberResponse from(Member member) {
		return new MemberResponse(member.getId(), member.getNickname(), member.getProfileImageUrl());
	}
}
