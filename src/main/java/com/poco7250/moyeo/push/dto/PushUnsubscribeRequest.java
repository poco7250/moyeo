package com.poco7250.moyeo.push.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 기기 푸시 구독 해제 요청.
 *
 * @param endpoint 해제할 구독의 푸시 서비스 주소
 */
public record PushUnsubscribeRequest(@NotBlank String endpoint) {
}
