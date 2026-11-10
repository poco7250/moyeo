package com.poco7250.moyeo.game.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 추첨 seed를 만든다.
 */
@Component
public class GameSeedGenerator {

	// JSON으로 나간 값을 JavaScript가 정확히 읽도록 안전한 정수 범위(2^53)로 제한한다
	private static final long MAX_EXCLUSIVE = 1L << 53;

	private final SecureRandom secureRandom = new SecureRandom();

	/**
	 * 새 seed를 만든다.
	 */
	public long next() {
		return secureRandom.nextLong(MAX_EXCLUSIVE);
	}
}
