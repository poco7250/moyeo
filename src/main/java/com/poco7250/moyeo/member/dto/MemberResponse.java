package com.poco7250.moyeo.member.dto;

import com.poco7250.moyeo.member.domain.Member;

/**
 * 화면에 내보내는 회원 정보. 비밀번호는 포함하지 않는다.
 */
public record MemberResponse(Long id, String email, String nickname) {

	/**
	 * 엔티티를 응답 DTO로 변환한다.
	 */
	public static MemberResponse from(Member member) {
		return new MemberResponse(member.getId(), member.getEmail(), member.getNickname());
	}
}
