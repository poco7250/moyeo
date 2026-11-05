package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.place.dto.RoundVotesResponse;

/**
 * 차수의 득표 현황이 바뀌었음을 모임 토픽에 알리는 이벤트. 커밋 후 RealtimeBroadcaster가 전송한다.
 * 재투표로 표가 초기화될 때는 PLACE_CHANGED만 나가므로 클라이언트가 현황을 다시 조회한다.
 */
public record VoteChangedEvent(Long meetingId, RoundVotesResponse data) implements RealtimeEvent {

	public static final String TYPE = "VOTE_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
