package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.group.domain.GroupMember;

/**
 * 현황판에 표시할 멤버(그룹 안 닉네임·이모지).
 */
public record BoardMember(Long memberId, String nickname, String emoji) {

	/**
	 * 그룹 멤버십으로 현황판 멤버를 만든다. 회원은 LAZY 프록시의 id만 읽는다.
	 */
	public static BoardMember from(GroupMember groupMember) {
		return new BoardMember(groupMember.getMember().getId(), groupMember.getNickname(), groupMember.getEmoji());
	}
}
