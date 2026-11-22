package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.meeting.dto.MeetingGuestsResponse;

/**
 * 모임 게스트가 추가·삭제됐음을 모임 토픽에 알리는 이벤트(G-6). 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record MeetingGuestsChangedEvent(Long meetingId, MeetingGuestsResponse data) implements RealtimeEvent {

	public static final String TYPE = "MEETING_GUESTS_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
