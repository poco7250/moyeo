package com.poco7250.moyeo.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.push.VapidProperties;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.push.domain.PushMute;
import com.poco7250.moyeo.push.domain.PushSubscription;
import com.poco7250.moyeo.push.domain.PushType;
import com.poco7250.moyeo.push.dto.PushKeysRequest;
import com.poco7250.moyeo.push.dto.PushPreferencesResponse;
import com.poco7250.moyeo.push.dto.PushSubscribeRequest;
import com.poco7250.moyeo.push.repository.PushMuteRepository;
import com.poco7250.moyeo.push.repository.PushSubscriptionRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PushSubscriptionService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2099, 12, 1, 12, 0);
	private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/phone";

	@Mock
	PushSubscriptionRepository pushSubscriptionRepository;

	@Mock
	PushMuteRepository pushMuteRepository;

	@Mock
	MemberRepository memberRepository;

	private final Member minsu = Member.ofKakao(1L, "민수", null, null);
	private final Member younghee = Member.ofKakao(2L, "영희", null, null);

	PushSubscriptionService service;

	@BeforeEach
	void setUp() {
		service = newService(new VapidProperties("public-key", "private-key", "mailto:a@b.c", Duration.ofSeconds(3),
				Duration.ofSeconds(5)));
	}

	@Test
	@DisplayName("새 기기는 저장하고, 같은 endpoint는 키를 갱신해 요청자 것으로 옮긴다")
	void subscribeAndRenew() {
		PushSubscription existing = PushSubscription.register(younghee, ENDPOINT, "old", "old",
				NOW.minusDays(1));
		given(memberRepository.getReferenceById(1L)).willReturn(minsu);
		given(pushSubscriptionRepository.findByEndpointHash(PushSubscription.hash(ENDPOINT)))
				.willReturn(Optional.of(existing));

		service.subscribe(1L, request(ENDPOINT));

		assertThat(existing.getMember()).isSameAs(minsu);
		assertThat(existing.getP256dh()).isEqualTo("new-p256dh");
		assertThat(existing.getRegisteredAt()).isEqualTo(NOW);
		verify(pushSubscriptionRepository, never()).save(any());
	}

	@Test
	@DisplayName("기기가 10개를 넘으면 가장 오래전에 등록한 기기부터 지운다")
	void trimOldDevices() {
		List<PushSubscription> devices = new ArrayList<>(IntStream.range(0, 11)
				.mapToObj(i -> PushSubscription.register(minsu, ENDPOINT + i, "k", "a", NOW.minusHours(i)))
				.toList());
		given(memberRepository.getReferenceById(1L)).willReturn(minsu);
		given(pushSubscriptionRepository.findByEndpointHash(any())).willReturn(Optional.empty());
		given(pushSubscriptionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
		given(pushSubscriptionRepository.findAllByMemberIdOrderByRegisteredAtDescIdDesc(1L)).willReturn(devices);

		service.subscribe(1L, request(ENDPOINT + "new"));

		verify(pushSubscriptionRepository).deleteAllInBatch(List.of(devices.get(10)));
	}

	@Test
	@DisplayName("받기 설정은 끈 종류만 false이고, 보낸 종류만 켜고 끈다")
	void preferences() {
		given(pushMuteRepository.findAllByMemberId(1L)).willReturn(List.of(PushMute.of(minsu, PushType.MEETING_CREATED)));
		given(memberRepository.getReferenceById(1L)).willReturn(minsu);
		Map<PushType, Boolean> change = new EnumMap<>(PushType.class);
		change.put(PushType.MEETING_CREATED, true);
		change.put(PushType.PLACE_CONFIRMED, false);
		change.put(PushType.DATE_CONFIRMED, true);

		PushPreferencesResponse response = service.updatePreferences(1L, change);

		assertThat(response.enabled()).containsExactly(Map.entry(PushType.ATTENDANCE_REMINDER, true),
				Map.entry(PushType.MEETING_CREATED, true), Map.entry(PushType.PLACE_CONFIRMED, false),
				Map.entry(PushType.DATE_CONFIRMED, true));
		verify(pushMuteRepository).deleteByMemberIdAndType(1L, PushType.MEETING_CREATED);
		verify(pushMuteRepository).save(any(PushMute.class));
		verify(pushMuteRepository, never()).deleteByMemberIdAndType(1L, PushType.DATE_CONFIRMED);
	}

	@Test
	@DisplayName("VAPID 키가 없으면 공개키 조회는 503 PUSH_NOT_CONFIGURED")
	void publicKeyNotConfigured() {
		PushSubscriptionService unconfigured = newService(
				new VapidProperties("", "", "mailto:a@b.c", Duration.ofSeconds(3), Duration.ofSeconds(5)));

		assertThat(service.getPublicKey().publicKey()).isEqualTo("public-key");
		assertThatThrownBy(unconfigured::getPublicKey).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PUSH_NOT_CONFIGURED);
	}

	private PushSubscriptionService newService(VapidProperties properties) {
		return new PushSubscriptionService(pushSubscriptionRepository, pushMuteRepository, memberRepository,
				properties, TestClocks.fixed(NOW));
	}

	private static PushSubscribeRequest request(String endpoint) {
		return new PushSubscribeRequest(endpoint, new PushKeysRequest("new-p256dh", "new-auth"));
	}
}
