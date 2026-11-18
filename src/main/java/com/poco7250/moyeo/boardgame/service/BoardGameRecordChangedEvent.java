package com.poco7250.moyeo.boardgame.service;

import com.poco7250.moyeo.boardgame.dto.BoardGameRecordResponse;
import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;

/**
 * 보드게임 기록이 생기거나 바뀌었음을 모임 토픽에 알리는 이벤트. 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record BoardGameRecordChangedEvent(Long meetingId, BoardGameRecordResponse data) implements RealtimeEvent {

	public static final String TYPE = "BOARD_GAME_RECORD_CHANGED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
