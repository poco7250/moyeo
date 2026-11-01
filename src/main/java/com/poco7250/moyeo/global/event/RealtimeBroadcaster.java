package com.poco7250.moyeo.global.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link RealtimeEvent}를 트랜잭션 커밋 뒤에만 토픽으로 브로드캐스트한다(PRD 6장).
 * fallbackExecution은 기본값(false)이라 트랜잭션 밖에서 발행한 이벤트는 보내지 않는다 —
 * 실시간 이벤트는 반드시 상태를 바꾸는 Service 트랜잭션 안에서 발행해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeBroadcaster {

	private final SimpMessageSendingOperations messagingTemplate;

	/**
	 * 커밋된 이벤트를 토픽으로 보낸다. 이미 커밋된 뒤라 전송 실패는 로그만 남기고,
	 * 클라이언트는 재연결·REST 조회로 상태를 복구한다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void broadcast(RealtimeEvent event) {
		try {
			messagingTemplate.convertAndSend(event.destination(), RealtimeMessage.from(event));
		} catch (MessagingException e) {
			log.warn("실시간 브로드캐스트 실패: destination={}, type={}", event.destination(), event.type(), e);
		}
	}
}
