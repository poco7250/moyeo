package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;

/**
 * 참석 응답이 바뀌었음을 모임 토픽에 알리는 이벤트. 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record AttendanceChangedEvent(Long meetingId, MemberAttendanceResponse data) implements RealtimeEvent {

	public static final String TYPE = "ATTENDANCE_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
