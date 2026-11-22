package com.poco7250.moyeo.history.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.history.service.YearReviewShareText.Ranked;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class YearReviewShareTextTest {

	@Test
	@DisplayName("모든 줄이 있으면 금액은 천 단위 쉼표, 공동 1위는 ·로 잇는다")
	void fullText() {
		String text = YearReviewShareText.of("대학 동기", 2026, 23, 1_420_000L,
				new Ranked(List.of("민수", "영희"), 20), new Ranked(List.of("강남 OO식당"), 5),
				new Ranked(List.of("영희"), 7));

		assertThat(text).isEqualTo("""
				[대학 동기] 2026년 결산
				올해 모임 23번 · 총 1,420,000원
				최다 참석: 민수·영희(20번)
				가장 많이 간 곳: 강남 OO식당(5번)
				벌칙왕: 영희(7번)""");
	}

	@Test
	@DisplayName("데이터가 없는 줄은 빼고, 지출이 0원이어도 0원으로 쓴다")
	void omitsEmptyLines() {
		String text = YearReviewShareText.of("대학 동기", 2026, 1, 0L, Ranked.NONE, Ranked.NONE, Ranked.NONE);

		assertThat(text).isEqualTo("[대학 동기] 2026년 결산\n올해 모임 1번 · 총 0원");
	}
}
