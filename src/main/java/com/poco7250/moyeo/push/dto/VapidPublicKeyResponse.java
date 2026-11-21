package com.poco7250.moyeo.push.dto;

/**
 * 브라우저 구독({@code pushManager.subscribe})의 applicationServerKey로 쓸 VAPID 공개키.
 *
 * @param publicKey base64url raw P-256 공개키
 */
public record VapidPublicKeyResponse(String publicKey) {
}
