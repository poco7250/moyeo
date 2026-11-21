package com.poco7250.moyeo.push.dto;

import com.poco7250.moyeo.global.push.EcKeys;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 구독 키(base64url). p256dh = 압축 안 한 P-256 공개키 65바이트, auth = 16바이트.
 */
public record PushKeysRequest(
		@NotBlank @Size(max = 100) String p256dh,
		@NotBlank @Size(max = 50) String auth) {

	private static final int AUTH_LENGTH = 16;

	/**
	 * p256dh가 base64url로 읽히고 P-256 공개키 형식(65바이트, 0x04로 시작)인지 본다. 비어 있으면 @NotBlank가 잡는다.
	 */
	@AssertTrue(message = "p256dh는 base64url P-256 공개키(65바이트)여야 합니다.")
	public boolean isP256dhValid() {
		byte[] decoded = decodeOrNull(p256dh);
		return p256dh == null || p256dh.isBlank()
				|| (decoded != null && decoded.length == EcKeys.PUBLIC_KEY_LENGTH && decoded[0] == 0x04);
	}

	/**
	 * auth가 base64url 16바이트인지 본다.
	 */
	@AssertTrue(message = "auth는 base64url 16바이트여야 합니다.")
	public boolean isAuthValid() {
		byte[] decoded = decodeOrNull(auth);
		return auth == null || auth.isBlank() || (decoded != null && decoded.length == AUTH_LENGTH);
	}

	private static byte[] decodeOrNull(String value) {
		if (value == null) {
			return null;
		}
		try {
			return EcKeys.decodeBase64Url(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
