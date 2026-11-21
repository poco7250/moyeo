package com.poco7250.moyeo.push.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.push.VapidProperties;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.push.domain.PushMute;
import com.poco7250.moyeo.push.domain.PushSubscription;
import com.poco7250.moyeo.push.domain.PushType;
import com.poco7250.moyeo.push.dto.PushPreferencesResponse;
import com.poco7250.moyeo.push.dto.PushSubscribeRequest;
import com.poco7250.moyeo.push.dto.PushSubscriptionResponse;
import com.poco7250.moyeo.push.dto.VapidPublicKeyResponse;
import com.poco7250.moyeo.push.repository.PushMuteRepository;
import com.poco7250.moyeo.push.repository.PushSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 본인의 기기 푸시 구독과 종류별 받기 설정(PRD 6장 PWA 웹 푸시, Task 056). 그룹과 무관한 본인 리소스다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

	private final PushSubscriptionRepository pushSubscriptionRepository;
	private final PushMuteRepository pushMuteRepository;
	private final MemberRepository memberRepository;
	private final VapidProperties vapidProperties;
	private final Clock clock;

	/**
	 * 브라우저 구독에 쓸 VAPID 공개키를 돌려준다.
	 *
	 * @throws BusinessException VAPID 키가 설정되지 않았으면 PUSH_NOT_CONFIGURED
	 */
	public VapidPublicKeyResponse getPublicKey() {
		if (!vapidProperties.isConfigured()) {
			throw new BusinessException(ErrorCode.PUSH_NOT_CONFIGURED);
		}
		return new VapidPublicKeyResponse(vapidProperties.publicKey());
	}

	/**
	 * 기기 구독을 등록한다. 같은 endpoint가 있으면 키를 갱신하고 요청자 것으로 옮긴다.
	 * 기기가 {@link PushSubscription#MAX_PER_MEMBER}개를 넘으면 가장 오래전에 등록한 기기부터 지운다.
	 */
	@Transactional
	public PushSubscriptionResponse subscribe(Long memberId, PushSubscribeRequest request) {
		Member member = memberRepository.getReferenceById(memberId);
		LocalDateTime now = LocalDateTime.now(clock);
		PushSubscription subscription = pushSubscriptionRepository
				.findByEndpointHash(PushSubscription.hash(request.endpoint()))
				.map(existing -> {
					existing.renew(member, request.keys().p256dh(), request.keys().auth(), now);
					return existing;
				})
				.orElseGet(() -> pushSubscriptionRepository.save(PushSubscription.register(member,
						request.endpoint(), request.keys().p256dh(), request.keys().auth(), now)));
		pushSubscriptionRepository.flush();
		trimOldDevices(memberId);
		return PushSubscriptionResponse.from(subscription);
	}

	/**
	 * 본인 기기 구독을 해제한다. 없거나 다른 회원 구독이면 아무것도 하지 않는다(멱등).
	 */
	@Transactional
	public void unsubscribe(Long memberId, String endpoint) {
		pushSubscriptionRepository.deleteMine(memberId, PushSubscription.hash(endpoint));
	}

	/**
	 * 종류별 받기 설정을 돌려준다.
	 */
	@Transactional(readOnly = true)
	public PushPreferencesResponse getPreferences(Long memberId) {
		return PushPreferencesResponse.fromMuted(mutedTypes(memberId));
	}

	/**
	 * 보낸 종류만 켜거나 끈다. 이미 같은 상태면 그대로 둔다.
	 */
	@Transactional
	public PushPreferencesResponse updatePreferences(Long memberId, Map<PushType, Boolean> enabled) {
		Set<PushType> muted = mutedTypes(memberId);
		Member member = memberRepository.getReferenceById(memberId);
		enabled.forEach((type, on) -> {
			if (on && muted.remove(type)) {
				pushMuteRepository.deleteByMemberIdAndType(memberId, type);
			} else if (!on && muted.add(type)) {
				pushMuteRepository.save(PushMute.of(member, type));
			}
		});
		return PushPreferencesResponse.fromMuted(muted);
	}

	private Set<PushType> mutedTypes(Long memberId) {
		return pushMuteRepository.findAllByMemberId(memberId).stream()
				.map(PushMute::getType)
				.collect(Collectors.toCollection(HashSet::new));
	}

	private void trimOldDevices(Long memberId) {
		List<PushSubscription> devices =
				pushSubscriptionRepository.findAllByMemberIdOrderByRegisteredAtDescIdDesc(memberId);
		if (devices.size() > PushSubscription.MAX_PER_MEMBER) {
			List<PushSubscription> stale = devices.subList(PushSubscription.MAX_PER_MEMBER, devices.size());
			pushSubscriptionRepository.deleteAllInBatch(stale);
			log.info("푸시 구독 기기 수 상한 정리: memberId={}, removed={}", memberId, stale.size());
		}
	}
}
