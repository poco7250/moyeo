package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.meeting.dto.MeetingHostResponse;

/**
 * 모임 호스트가 바뀌었음을 모임 토픽에 알리는 이벤트(G-4). 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record MeetingHostChangedEvent(Long meetingId, MeetingHostResponse data) implements RealtimeEvent {

	public static final String TYPE = "MEETING_HOST_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
