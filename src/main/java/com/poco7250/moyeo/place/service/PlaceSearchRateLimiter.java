package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.place.config.PlaceSearchProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 회원별 장소 검색 빈도 제한(메모리 고정 윈도우).
 * 카카오 로컬 결과는 캐싱할 수 없어 호출량을 서버에서 직접 줄인다. 단일 인스턴스 전제(MVP)라 다중 인스턴스면 공유 저장소로 바꿔야 한다.
 */
@Component
public class PlaceSearchRateLimiter {

	// 이 수를 넘으면 지난 윈도우 항목을 정리한다(4~12명 규모라 평소엔 거의 안 탄다)
	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Map<Long, Window> windows = new ConcurrentHashMap<>();
	private final PlaceSearchProperties.RateLimit rateLimit;
	private final Clock clock;

	/**
	 * 설정의 빈도 제한 값과 시간 기준 Clock으로 만든다.
	 */
	public PlaceSearchRateLimiter(PlaceSearchProperties properties, Clock clock) {
		this.rateLimit = properties.rateLimit();
		this.clock = clock;
	}

	/**
	 * 회원의 검색 1회를 기록한다. 현재 윈도우의 한도를 넘으면 기록하지 않고 거부한다.
	 *
	 * @throws BusinessException 한도 초과면 PLACE_SEARCH_RATE_LIMITED
	 */
	public void acquire(Long memberId) {
		Instant now = clock.instant();
		cleanUpIfLarge(now);
		Window window = windows.compute(memberId, (id, current) -> next(current, now));
		if (window.count() > rateLimit.maxRequests()) {
			throw new BusinessException(ErrorCode.PLACE_SEARCH_RATE_LIMITED);
		}
	}

	/**
	 * 윈도우가 지났으면 새로 시작하고, 아니면 횟수를 1 늘린다(한도를 넘은 뒤로는 더 늘리지 않음).
	 */
	private Window next(Window current, Instant now) {
		if (current == null || isExpired(current, now)) {
			return new Window(now, 1);
		}
		return new Window(current.startedAt(), Math.min(current.count() + 1, rateLimit.maxRequests() + 1));
	}

	/**
	 * 윈도우 시작 시각에서 윈도우 길이만큼 지났으면(정각 포함) 만료로 본다.
	 */
	private boolean isExpired(Window window, Instant now) {
		return !now.isBefore(window.startedAt().plus(rateLimit.window()));
	}

	/**
	 * 항목이 많이 쌓였으면 만료된 윈도우를 지운다.
	 */
	private void cleanUpIfLarge(Instant now) {
		if (windows.size() > CLEANUP_THRESHOLD) {
			windows.values().removeIf(window -> isExpired(window, now));
		}
	}

	/**
	 * 회원 한 명의 현재 윈도우.
	 */
	private record Window(Instant startedAt, int count) {
	}
}
