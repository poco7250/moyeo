package com.poco7250.moyeo.place.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.place.config.PlaceSearchProperties;
import com.poco7250.moyeo.support.TestClocks;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceSearchRateLimiterTest {

	private static final int MAX = 3;

	private MutableClock clock;
	private PlaceSearchRateLimiter limiter;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(TestClocks.fixed(LocalDateTime.of(2026, 11, 3, 12, 0)).instant());
		PlaceSearchProperties properties = new PlaceSearchProperties(15,
				new PlaceSearchProperties.RateLimit(MAX, Duration.ofMinutes(1)));
		limiter = new PlaceSearchRateLimiter(properties, clock);
	}

	@Test
	@DisplayName("윈도우 안에서 한도까지는 허용하고 넘으면 PLACE_SEARCH_RATE_LIMITED")
	void limitWithinWindow() {
		for (int i = 0; i < MAX; i++) {
			assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
		}

		assertRateLimited(1L);
		assertRateLimited(1L);
	}

	@Test
	@DisplayName("윈도우가 끝나기 직전은 거부, 정각이 되면 다시 허용한다")
	void resetAfterWindow() {
		exhaust(1L);

		clock.advance(Duration.ofSeconds(59));
		assertRateLimited(1L);

		clock.advance(Duration.ofSeconds(1));
		assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("회원마다 한도를 따로 센다")
	void perMember() {
		exhaust(1L);

		assertRateLimited(1L);
		assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
	}

	/**
	 * 회원의 한도를 다 쓴다.
	 */
	private void exhaust(Long memberId) {
		for (int i = 0; i < MAX; i++) {
			limiter.acquire(memberId);
		}
	}

	/**
	 * 빈도 제한으로 거부되는지 확인한다.
	 */
	private void assertRateLimited(Long memberId) {
		assertThatThrownBy(() -> limiter.acquire(memberId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.PLACE_SEARCH_RATE_LIMITED);
	}

	/**
	 * 테스트에서 시간을 앞으로 돌릴 수 있는 Clock.
	 */
	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return TestClocks.SEOUL;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
