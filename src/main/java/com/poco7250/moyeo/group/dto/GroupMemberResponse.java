package com.poco7250.moyeo.group.dto;

import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;

/**
 * 그룹 멤버 응답. 닉네임·이모지는 그룹 안에서 쓰는 값이다.
 */
public record GroupMemberResponse(Long memberId, String nickname, String emoji, GroupRole role) {

	/**
	 * 엔티티를 응답 DTO로 변환한다. 회원은 id만 읽어 프록시를 초기화하지 않는다.
	 */
	public static GroupMemberResponse from(GroupMember groupMember) {
		return new GroupMemberResponse(groupMember.getMember().getId(), groupMember.getNickname(),
				groupMember.getEmoji(), groupMember.getRole());
	}
}
