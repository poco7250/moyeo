package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.meeting.dto.CheckInResponse;

/**
 * 체크인 여부가 바뀌었음을 모임 토픽에 알리는 이벤트(PRD M-7). 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record CheckInChangedEvent(Long meetingId, CheckInResponse data) implements RealtimeEvent {

	public static final String TYPE = "CHECK_IN_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
