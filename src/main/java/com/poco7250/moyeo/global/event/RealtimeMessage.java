package com.poco7250.moyeo.global.event;

/**
 * 토픽으로 나가는 메시지 공통 형식. 클라이언트는 type으로 분기하고 data를 읽는다.
 *
 * @param type 메시지 종류
 * @param data 본문 DTO
 */
public record RealtimeMessage(String type, Object data) {

	/**
	 * 이벤트에서 메시지를 만든다.
	 */
	public static RealtimeMessage from(RealtimeEvent event) {
		return new RealtimeMessage(event.type(), event.data());
	}
}
