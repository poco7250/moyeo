package com.poco7250.moyeo.push.controller;

import com.poco7250.moyeo.auth.security.LoginMember;
import com.poco7250.moyeo.global.common.ApiResponse;
import com.poco7250.moyeo.push.dto.PushPreferencesRequest;
import com.poco7250.moyeo.push.dto.PushPreferencesResponse;
import com.poco7250.moyeo.push.dto.PushSubscribeRequest;
import com.poco7250.moyeo.push.dto.PushSubscriptionResponse;
import com.poco7250.moyeo.push.dto.PushUnsubscribeRequest;
import com.poco7250.moyeo.push.dto.VapidPublicKeyResponse;
import com.poco7250.moyeo.push.service.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 푸시 구독·받기 설정 API(Task 056). 전부 로그인이 필요하고 본인 것만 다룬다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PushController {

	private final PushSubscriptionService pushSubscriptionService;

	/**
	 * 브라우저 구독에 쓸 VAPID 공개키를 돌려준다(서버에 키가 없으면 503).
	 */
	@GetMapping("/push/vapid-public-key")
	public ApiResponse<VapidPublicKeyResponse> publicKey() {
		return ApiResponse.ok(pushSubscriptionService.getPublicKey());
	}

	/**
	 * 이 기기의 푸시 구독을 등록(갱신)한다.
	 */
	@PostMapping("/members/me/push-subscriptions")
	public ApiResponse<PushSubscriptionResponse> subscribe(@LoginMember Long memberId,
			@Valid @RequestBody PushSubscribeRequest request) {
		return ApiResponse.ok(pushSubscriptionService.subscribe(memberId, request));
	}

	/**
	 * 이 기기의 푸시 구독을 해제한다(없어도 성공).
	 */
	@DeleteMapping("/members/me/push-subscriptions")
	public ApiResponse<Void> unsubscribe(@LoginMember Long memberId,
			@Valid @RequestBody PushUnsubscribeRequest request) {
		pushSubscriptionService.unsubscribe(memberId, request.endpoint());
		return ApiResponse.ok();
	}

	/**
	 * 푸시 종류별 받기 설정을 돌려준다.
	 */
	@GetMapping("/members/me/push-preferences")
	public ApiResponse<PushPreferencesResponse> preferences(@LoginMember Long memberId) {
		return ApiResponse.ok(pushSubscriptionService.getPreferences(memberId));
	}

	/**
	 * 보낸 종류만 켜거나 끄고 전체 설정을 돌려준다.
	 */
	@PatchMapping("/members/me/push-preferences")
	public ApiResponse<PushPreferencesResponse> updatePreferences(@LoginMember Long memberId,
			@Valid @RequestBody PushPreferencesRequest request) {
		return ApiResponse.ok(pushSubscriptionService.updatePreferences(memberId, request.enabled()));
	}
}
