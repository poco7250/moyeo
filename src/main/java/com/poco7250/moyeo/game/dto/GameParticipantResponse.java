package com.poco7250.moyeo.game.dto;

import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.member.domain.Member;

/**
 * 게임 참가자 프로필. 그룹 멤버는 그룹 닉네임·이모지, 모임 게스트(G-6)는 게스트 이름이고 이모지가 없다.
 *
 * @param guest 모임 게스트면 true
 */
public record GameParticipantResponse(Long memberId, String nickname, String emoji, boolean guest) {

	/**
	 * 그룹 멤버 프로필로 응답을 만든다.
	 */
	public static GameParticipantResponse from(GroupMember groupMember) {
		return new GameParticipantResponse(groupMember.getMember().getId(), groupMember.getNickname(),
				groupMember.getEmoji(), false);
	}

	/**
	 * 모임 게스트 회원으로 응답을 만든다.
	 */
	public static GameParticipantResponse guest(Member guest) {
		return new GameParticipantResponse(guest.getId(), guest.getNickname(), null, true);
	}
}
