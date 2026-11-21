package com.poco7250.moyeo.global.push;

/**
 * 푸시를 받을 기기 하나(브라우저 PushSubscription).
 *
 * @param endpoint 푸시 서비스 주소
 * @param p256dh   기기 공개키(base64url)
 * @param auth     기기 인증 비밀값(base64url)
 */
public record PushTarget(String endpoint, String p256dh, String auth) {
}
