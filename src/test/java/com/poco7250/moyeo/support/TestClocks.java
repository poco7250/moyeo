package com.poco7250.moyeo.support;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 테스트에서 시간을 고정하기 위한 Clock 헬퍼.
 */
public final class TestClocks {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private TestClocks() {
	}

	/**
	 * 주어진 서울 시각에 멈춰 있는 Clock을 만든다.
	 */
	public static Clock fixed(LocalDateTime dateTime) {
		return Clock.fixed(dateTime.atZone(SEOUL).toInstant(), SEOUL);
	}
}
