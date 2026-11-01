package com.poco7250.moyeo.group.token;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 초대 링크 토큰을 만든다. 추측할 수 없도록 SecureRandom을 쓰고, URL에 바로 넣을 수 있게 Base64URL로 인코딩한다.
 */
@Component
public class InviteTokenGenerator {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	/**
	 * 32바이트 난수를 Base64URL(패딩 없음) 43자로 만든다.
	 */
	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
