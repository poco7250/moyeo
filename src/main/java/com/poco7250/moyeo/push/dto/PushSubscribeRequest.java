package com.poco7250.moyeo.push.dto;

import com.poco7250.moyeo.global.push.PushEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 기기 푸시 구독 등록 요청. 브라우저 {@code PushSubscription.toJSON()}의 endpoint·keys를 그대로 보낸다.
 *
 * @param endpoint 푸시 서비스 주소(알려진 푸시 서비스만)
 * @param keys     기기 공개키(p256dh)·인증 비밀값(auth)
 */
public record PushSubscribeRequest(
		@NotBlank @PushEndpoint String endpoint,
		@NotNull @Valid PushKeysRequest keys) {
}
