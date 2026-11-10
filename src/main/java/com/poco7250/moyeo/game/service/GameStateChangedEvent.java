package com.poco7250.moyeo.game.service;

import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.global.event.RealtimeEvent;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;

/**
 * 게임 방 상태가 바뀌었음을 알리는 이벤트. 커밋 후 RealtimeBroadcaster가 전송한다.
 * 게임 토픽을 아직 구독하지 않은 참가자도 받도록 모임 토픽에도 같은 내용을 보낸다.
 */
public record GameStateChangedEvent(String destination, GameStateResponse data) implements RealtimeEvent {

	public static final String TYPE = "GAME_STATE_CHANGED";

	/**
	 * 게임 토픽으로 보낼 이벤트를 만든다.
	 */
	public static GameStateChangedEvent toGame(Long gameId, GameStateResponse data) {
		return new GameStateChangedEvent(RealtimeTopics.game(gameId), data);
	}

	/**
	 * 모임 토픽으로 보낼 이벤트를 만든다.
	 */
	public static GameStateChangedEvent toMeeting(Long meetingId, GameStateResponse data) {
		return new GameStateChangedEvent(RealtimeTopics.meeting(meetingId), data);
	}

	@Override
	public String type() {
		return TYPE;
	}
}
