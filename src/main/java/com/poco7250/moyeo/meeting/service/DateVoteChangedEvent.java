package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.meeting.dto.MeetingDateVotesResponse;

/**
 * 날짜 후보·응답이 바뀌었음을 모임 토픽에 알리는 이벤트. 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record DateVoteChangedEvent(Long meetingId, MeetingDateVotesResponse data) implements RealtimeEvent {

	public static final String TYPE = "DATE_VOTE_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
