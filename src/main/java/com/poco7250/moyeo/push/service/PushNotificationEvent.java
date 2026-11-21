package com.poco7250.moyeo.push.service;

import com.poco7250.moyeo.push.domain.PushType;
import java.util.Set;

/**
 * 커밋 후 보낼 웹 푸시. Service는 트랜잭션 안에서 {@code ApplicationEventPublisher.publishEvent(...)}로 발행만 하고,
 * 실제 전송은 {@link PushDispatcher}가 커밋된 뒤 별도 스레드에서 한다(롤백되면 아무것도 나가지 않음).
 *
 * @param type         푸시 종류(회원 수신 설정 기준)
 * @param recipientIds 받을 회원 id
 * @param title        알림 제목
 * @param body         알림 본문
 * @param url          알림을 누르면 열 주소(모임 상세)
 * @param tag          같은 태그 알림은 기기에서 새 알림으로 바꿔 쌓이지 않게 한다
 */
public record PushNotificationEvent(PushType type, Set<Long> recipientIds, String title, String body, String url,
		String tag) {

	public PushNotificationEvent {
		recipientIds = Set.copyOf(recipientIds);
	}

	/**
	 * 서비스 워커가 받는 JSON 페이로드로 바꾼다.
	 */
	PushPayload toPayload() {
		return new PushPayload(type, title, body, url, tag);
	}

	/**
	 * 서비스 워커로 가는 푸시 페이로드. 프론트는 type으로 분기하고 title·body로 알림을 띄운다.
	 */
	record PushPayload(PushType type, String title, String body, String url, String tag) {
	}
}
