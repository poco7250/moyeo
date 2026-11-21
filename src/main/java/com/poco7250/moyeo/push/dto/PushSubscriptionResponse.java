package com.poco7250.moyeo.push.dto;

import com.poco7250.moyeo.push.domain.PushSubscription;
import java.time.LocalDateTime;

/**
 * 등록한 기기 구독. 키는 돌려주지 않는다.
 */
public record PushSubscriptionResponse(Long id, String endpoint, LocalDateTime registeredAt) {

	/**
	 * 구독 엔티티로 응답을 만든다.
	 */
	public static PushSubscriptionResponse from(PushSubscription subscription) {
		return new PushSubscriptionResponse(subscription.getId(), subscription.getEndpoint(),
				subscription.getRegisteredAt());
	}
}
