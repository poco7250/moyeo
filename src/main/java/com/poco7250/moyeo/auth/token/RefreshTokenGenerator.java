package com.poco7250.moyeo.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Refresh 토큰 원문을 만들고, DB에 저장할 해시를 계산한다.
 * 원문은 클라이언트 쿠키에만 있고, DB에는 SHA-256 해시만 남긴다(DB가 새도 토큰을 재사용할 수 없도록).
 */
@Component
public class RefreshTokenGenerator {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	/**
	 * 32바이트 난수를 Base64URL(패딩 없음)로 인코딩한 원문을 만든다.
	 */
	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * 원문의 SHA-256 해시를 소문자 hex 64자로 돌려준다.
	 */
	public String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			// SHA-256은 모든 JVM이 지원해야 하는 알고리즘이라 여기 오면 실행 환경 문제다
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
		}
	}
}
