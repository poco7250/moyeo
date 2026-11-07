package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.place.dto.PlaceDecisionRequiredResponse;

/**
 * 투표 마감에서 자동 확정하지 못해 호스트의 선택이 필요함을 모임 토픽에 알리는 이벤트.
 * 호스트 전용 채널이 없어 모임 전체에 나가고, 프론트가 호스트에게만 선택 화면을 보여 준다.
 */
public record PlaceDecisionRequiredEvent(Long meetingId, PlaceDecisionRequiredResponse data) implements RealtimeEvent {

	public static final String TYPE = "PLACE_DECISION_REQUIRED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
