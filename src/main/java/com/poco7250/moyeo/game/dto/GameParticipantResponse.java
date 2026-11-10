package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.group.domain.GroupMember;

/**
 * 게임 참가자의 그룹 프로필.
 */
public record GameParticipantResponse(Long memberId, String nickname, String emoji) {

	/**
	 * 그룹 멤버 프로필로 응답을 만든다.
	 */
	public static GameParticipantResponse from(GroupMember groupMember) {
		return new GameParticipantResponse(groupMember.getMember().getId(), groupMember.getNickname(),
				groupMember.getEmoji());
	}
}
