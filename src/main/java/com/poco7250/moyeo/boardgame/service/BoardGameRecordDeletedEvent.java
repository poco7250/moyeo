package com.poco7250.moyeo.boardgame.service;

import com.poco7250.moyeo.boardgame.dto.BoardGameRecordDeletedResponse;
import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;

/**
 * 보드게임 기록이 지워졌음을 모임 토픽에 알리는 이벤트. 커밋 후 RealtimeBroadcaster가 전송한다.
 */
public record BoardGameRecordDeletedEvent(Long meetingId, BoardGameRecordDeletedResponse data)
		implements RealtimeEvent {

	public static final String TYPE = "BOARD_GAME_RECORD_DELETED";

	@Override
	public String destination() {
		return RealtimeTopics.meeting(meetingId);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
