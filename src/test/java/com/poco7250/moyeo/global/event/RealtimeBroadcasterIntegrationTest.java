package com.poco7250.moyeo.global.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RealtimeBroadcasterIntegrationTest {

	// 내부 빈들이 SimpMessagingTemplate 타입을 요구하므로 목 대신 실제 빈을 감싼 스파이로 호출만 검증한다
	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	@Autowired
	ApplicationEventPublisher eventPublisher;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final TestEvent event = new TestEvent(RealtimeTopics.meeting(1L), "TEST_CHANGED", "본문");

	@Test
	@DisplayName("트랜잭션이 커밋되면 커밋 뒤에 한 번 브로드캐스트한다")
	void broadcastAfterCommit() {
		transactionTemplate.executeWithoutResult(status -> {
			eventPublisher.publishEvent(event);
			// 커밋 전에는 아직 보내지 않는다
			verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
		});

		verify(messagingTemplate).convertAndSend("/topic/meetings/1", new RealtimeMessage("TEST_CHANGED", "본문"));
	}

	@Test
	@DisplayName("트랜잭션이 롤백되면 아무것도 브로드캐스트하지 않는다")
	void noBroadcastOnRollback() {
		transactionTemplate.executeWithoutResult(status -> {
			eventPublisher.publishEvent(event);
			status.setRollbackOnly();
		});

		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	@DisplayName("트랜잭션 밖에서 발행한 이벤트는 보내지 않는다")
	void noBroadcastWithoutTransaction() {
		eventPublisher.publishEvent(event);

		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	/** 테스트용 실시간 이벤트 */
	record TestEvent(String destination, String type, Object data) implements RealtimeEvent {
	}
}
