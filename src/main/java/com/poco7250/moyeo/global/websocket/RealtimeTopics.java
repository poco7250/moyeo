package com.poco7250.moyeo.global.websocket;

/**
 * STOMP 토픽 규약.
 * <ul>
 *   <li>{@code /topic/meetings/{meetingId}} — 모임 단위 채널. 참석 현황·장소 투표·정산 상태 변경을 여기로 보낸다</li>
 *   <li>{@code /topic/games/{gameId}} — 게임 방 채널. 로비·진행·공개 상태를 여기로 보낸다</li>
 * </ul>
 * 서버만 토픽에 발행하고(클라이언트의 /topic SEND는 거부), 구독은 토픽이 속한 그룹의 멤버만 할 수 있다.
 * 토픽 → 그룹 해석은 도메인별 {@link TopicGroupResolver}가 맡는다.
 * 메시지 본문은 {@code com.poco7250.moyeo.global.event.RealtimeMessage}(type, data) 형식이다.
 */
public final class RealtimeTopics {

	/** 서버 발행 토픽 prefix(simple broker 대상) */
	public static final String TOPIC_PREFIX = "/topic/";

	/** 모임 토픽 prefix */
	public static final String MEETING_PREFIX = TOPIC_PREFIX + "meetings/";

	/** 게임 토픽 prefix */
	public static final String GAME_PREFIX = TOPIC_PREFIX + "games/";

	private RealtimeTopics() {
	}

	/**
	 * 모임 토픽 경로를 만든다.
	 */
	public static String meeting(Long meetingId) {
		return MEETING_PREFIX + meetingId;
	}

	/**
	 * 게임 토픽 경로를 만든다.
	 */
	public static String game(Long gameId) {
		return GAME_PREFIX + gameId;
	}
}
