package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.place.dto.PlaceRouletteResponse;

/**
 * 장소 동점 룰렛 결과를 모임 토픽에 알리는 이벤트. 멤버 화면이 seed로 룰렛 연출을 재생한다.
 * 확정된 장소 상태는 같은 트랜잭션의 PLACE_CHANGED로 따로 나간다.
 */
public record PlaceRouletteSpunEvent(Long meetingId, PlaceRouletteResponse data) implements RealtimeEvent {

	public static final String TYPE = "PLACE_ROULETTE_SPUN";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
