package com.poco7250.moyeo.history.dto;

import com.poco7250.moyeo.group.domain.GroupMember;

/**
 * 히스토리에 나오는 멤버(그룹 닉네임·이모지).
 */
public record HistoryMemberResponse(Long memberId, String nickname, String emoji) {

	/**
	 * 그룹 멤버십으로 응답을 만든다. 멤버십이 없으면(그룹을 떠난 경우 대비) 닉네임·이모지를 비운다.
	 */
	public static HistoryMemberResponse of(Long memberId, GroupMember groupMember) {
		return groupMember == null ? new HistoryMemberResponse(memberId, null, null)
				: new HistoryMemberResponse(memberId, groupMember.getNickname(), groupMember.getEmoji());
	}
}
