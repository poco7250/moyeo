package com.poco7250.moyeo.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.push.PushSendResult;
import com.poco7250.moyeo.global.push.PushTarget;
import com.poco7250.moyeo.global.push.WebPushClient;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.push.domain.PushSubscription;
import com.poco7250.moyeo.push.domain.PushType;
import com.poco7250.moyeo.push.repository.PushSubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * PushDispatcher 단위 테스트. 전송기·저장소는 목이다.
 */
@ExtendWith(MockitoExtension.class)
class PushDispatcherTest {

	private static final PushNotificationEvent EVENT = new PushNotificationEvent(PushType.ATTENDANCE_REMINDER,
			Set.of(1L, 2L), "연말 모임 참석 응답 부탁해", "응답 부탁해!", "/meetings/10", "meeting-10-attendance_reminder");

	@Mock
	PushSubscriptionRepository pushSubscriptionRepository;

	@Mock
	WebPushClient webPushClient;

	PushDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new PushDispatcher(pushSubscriptionRepository, webPushClient, JsonMapper.builder().build());
	}

	@Test
	@DisplayName("끄지 않은 회원의 기기마다 종류별 TTL로 JSON 페이로드를 보내고, 404·410 기기만 지운다")
	void dispatchAndRemoveGone() {
		PushSubscription phone = subscription(11L, "https://fcm.googleapis.com/fcm/send/phone");
		PushSubscription laptop = subscription(12L, "https://fcm.googleapis.com/fcm/send/laptop");
		PushSubscription broken = subscription(13L, "https://fcm.googleapis.com/fcm/send/broken");
		given(webPushClient.isEnabled()).willReturn(true);
		given(pushSubscriptionRepository.findDeliverable(Set.of(1L, 2L), PushType.ATTENDANCE_REMINDER))
				.willReturn(List.of(phone, laptop, broken));
		given(webPushClient.send(eq(phone.toTarget()), any(), any())).willReturn(PushSendResult.SENT);
		given(webPushClient.send(eq(laptop.toTarget()), any(), any())).willReturn(PushSendResult.GONE);
		given(webPushClient.send(eq(broken.toTarget()), any(), any())).willReturn(PushSendResult.FAILED);

		dispatcher.dispatch(EVENT);

		ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
		verify(webPushClient).send(eq(phone.toTarget()), payload.capture(), eq(Duration.ofHours(6)));
		assertThat(new String(payload.getValue(), StandardCharsets.UTF_8))
				.contains("\"type\":\"ATTENDANCE_REMINDER\"", "\"title\":\"연말 모임 참석 응답 부탁해\"",
						"\"url\":\"/meetings/10\"", "\"tag\":\"meeting-10-attendance_reminder\"");
		verify(pushSubscriptionRepository).deleteAllByIdInBatch(List.of(12L));
	}

	@Test
	@DisplayName("모두 성공하면 아무것도 지우지 않는다")
	void nothingGone() {
		PushSubscription phone = subscription(11L, "https://fcm.googleapis.com/fcm/send/phone");
		given(webPushClient.isEnabled()).willReturn(true);
		given(pushSubscriptionRepository.findDeliverable(any(), any())).willReturn(List.of(phone));
		given(webPushClient.send(any(PushTarget.class), any(), any())).willReturn(PushSendResult.SENT);

		dispatcher.dispatch(EVENT);

		verify(pushSubscriptionRepository, never()).deleteAllByIdInBatch(any());
	}

	@Test
	@DisplayName("VAPID 키가 없으면 구독을 읽지도 않는다")
	void disabled() {
		given(webPushClient.isEnabled()).willReturn(false);

		dispatcher.dispatch(EVENT);

		verifyNoInteractions(pushSubscriptionRepository);
	}

	private static PushSubscription subscription(Long id, String endpoint) {
		PushSubscription subscription = PushSubscription.register(Member.ofKakao(id, "회원", null, null), endpoint,
				"p256dh-" + id, "auth-" + id, LocalDateTime.of(2099, 1, 1, 0, 0));
		ReflectionTestUtils.setField(subscription, "id", id);
		return subscription;
	}
}
