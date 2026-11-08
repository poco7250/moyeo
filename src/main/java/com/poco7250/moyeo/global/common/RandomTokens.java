package com.poco7250.moyeo.global.common;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 링크에 넣는 추측 불가능한 토큰을 만든다. URL에 바로 넣을 수 있게 Base64URL로 인코딩한다.
 */
public final class RandomTokens {

	public static final int LENGTH = 43;

	private static final int TOKEN_BYTES = 32;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private RandomTokens() {
	}

	/**
	 * 32바이트 난수를 Base64URL(패딩 없음) 43자로 만든다.
	 */
	public static String urlSafe() {
		byte[] bytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
