package com.poco7250.moyeo.global.event;

/**
 * 커밋 후 WebSocket으로 내보낼 도메인 이벤트.
 * Service는 트랜잭션 안에서 {@code ApplicationEventPublisher.publishEvent(...)}로 발행만 하고,
 * 실제 전송은 {@link RealtimeBroadcaster}가 커밋된 뒤에 한다(롤백되면 아무것도 나가지 않음).
 * 구현은 도메인별 record로 만든다(예: AttendanceChangedEvent).
 */
public interface RealtimeEvent {

	/**
	 * 보낼 토픽 경로. {@code RealtimeTopics.meeting(id)}·{@code RealtimeTopics.game(id)}로 만든다.
	 */
	String destination();

	/**
	 * 클라이언트가 분기할 메시지 종류(예: ATTENDANCE_CHANGED).
	 */
	String type();

	/**
	 * 메시지 본문 DTO. 엔티티를 직접 넣지 않는다.
	 */
	Object data();
}
