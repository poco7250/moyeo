package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.MeetingGuest;

/**
 * 모임 게스트 한 명.
 *
 * @param guestId 게스트 회원 id. 게임 참가자로 넣을 때 participantMemberIds에 그대로 쓴다
 */
public record MeetingGuestResponse(Long guestId, String name) {

	/**
	 * 게스트 엔티티로 응답을 만든다.
	 */
	public static MeetingGuestResponse from(MeetingGuest guest) {
		return new MeetingGuestResponse(guest.getMember().getId(), guest.name());
	}
}
