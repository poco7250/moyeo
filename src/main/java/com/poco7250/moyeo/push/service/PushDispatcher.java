package com.poco7250.moyeo.push.service;

import com.poco7250.moyeo.global.config.AsyncConfig;
import com.poco7250.moyeo.global.push.PushSendResult;
import com.poco7250.moyeo.global.push.WebPushClient;
import com.poco7250.moyeo.push.domain.PushSubscription;
import com.poco7250.moyeo.push.repository.PushSubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link PushNotificationEvent}를 커밋 뒤에 별도 스레드에서 기기마다 보낸다. 요청 응답은 전송을 기다리지 않는다.
 * fallbackExecution은 기본값(false)이라 트랜잭션 밖에서 발행한 이벤트는 보내지 않는다.
 * 이미 커밋된 뒤라 실패는 로그만 남기고, 구독이 사라진 기기(404·410)는 지운다. 재시도는 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatcher {

	private final PushSubscriptionRepository pushSubscriptionRepository;
	private final WebPushClient webPushClient;
	private final JsonMapper jsonMapper;

	/**
	 * 받는 회원 중 그 종류를 끄지 않은 회원의 모든 기기에 보낸다.
	 */
	@Async(AsyncConfig.PUSH_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void dispatch(PushNotificationEvent event) {
		if (!webPushClient.isEnabled() || event.recipientIds().isEmpty()) {
			return;
		}
		List<PushSubscription> targets = pushSubscriptionRepository.findDeliverable(event.recipientIds(), event.type());
		byte[] payload = jsonMapper.writeValueAsString(event.toPayload()).getBytes(StandardCharsets.UTF_8);
		List<Long> gone = targets.stream()
				.filter(s -> webPushClient.send(s.toTarget(), payload, event.type().getTtl()) == PushSendResult.GONE)
				.map(PushSubscription::getId)
				.toList();
		if (!gone.isEmpty()) {
			pushSubscriptionRepository.deleteAllByIdInBatch(gone);
		}
		log.info("웹 푸시 전송: type={}, recipients={}, devices={}, gone={}", event.type(),
				event.recipientIds().size(), targets.size(), gone.size());
	}
}
