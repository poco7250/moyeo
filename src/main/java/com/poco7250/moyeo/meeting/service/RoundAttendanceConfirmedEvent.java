package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.meeting.dto.AttendanceConfirmResponse;

/**
 * 차수 실제 참석자가 확정됐음을 모임 토픽에 알리는 이벤트(PRD M-7). 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record RoundAttendanceConfirmedEvent(Long meetingId, AttendanceConfirmResponse data) implements RealtimeEvent {

	public static final String TYPE = "ROUND_ATTENDANCE_CONFIRMED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
