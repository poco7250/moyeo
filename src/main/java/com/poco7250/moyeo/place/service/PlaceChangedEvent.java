package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;

/**
 * 차수의 장소 결정 상태(후보 추가·바로 확정·투표 열기)가 바뀌었음을 모임 토픽에 알리는 이벤트.
 * 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record PlaceChangedEvent(Long meetingId, RoundPlaceResponse data) implements RealtimeEvent {

	public static final String TYPE = "PLACE_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
