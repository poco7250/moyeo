package com.poco7250.moyeo.history.service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 명예의 전당·연말 결산의 집계 기간 [from, to). 지금 이전 모임만 세고, 연도를 주면 그해(서울 시간)로 좁힌다.
 */
record HistoryPeriod(LocalDateTime from, LocalDateTime to) {

	// 전체 기간 조회의 시작 시각(서비스 시작 전이라 어떤 모임보다도 이르다)
	static final LocalDateTime EARLIEST = LocalDateTime.of(2000, 1, 1, 0, 0);

	/**
	 * 연도의 집계 기간을 만든다. 끝은 다음 해 1월 1일과 지금 중 이른 쪽이다.
	 *
	 * @param year 집계 연도, 전체 기간이면 null
	 */
	static HistoryPeriod of(Integer year, Clock clock) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (year == null) {
			return new HistoryPeriod(EARLIEST, now);
		}
		LocalDateTime nextYear = LocalDateTime.of(year + 1, 1, 1, 0, 0);
		return new HistoryPeriod(LocalDateTime.of(year, 1, 1, 0, 0), nextYear.isBefore(now) ? nextYear : now);
	}
}
