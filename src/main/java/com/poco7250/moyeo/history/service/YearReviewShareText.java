package com.poco7250.moyeo.history.service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 연말 결산 카카오톡 공유 문구를 만든다(PRD H-4). 사용자가 입력한 값만 쓰고 링크는 넣지 않는다.
 */
final class YearReviewShareText {

	private static final String NAME_SEPARATOR = "·";

	private YearReviewShareText() {
	}

	/**
	 * 결산 문구를 만든다. 데이터가 없는 줄(최다 참석·많이 간 곳·벌칙왕)은 뺀다.
	 *
	 * @param topAttendees 참석 1위 닉네임과 참석 횟수(없으면 {@link Ranked#NONE})
	 * @param topPlace 가장 많이 간 장소 이름과 방문 횟수(없으면 {@link Ranked#NONE})
	 * @param penaltyKings 벌칙왕 닉네임과 당첨 횟수(없으면 {@link Ranked#NONE})
	 */
	static String of(String groupName, int year, long meetingCount, long totalExpense, Ranked topAttendees,
			Ranked topPlace, Ranked penaltyKings) {
		List<String> lines = new ArrayList<>();
		lines.add("[%s] %d년 결산".formatted(groupName, year));
		lines.add("올해 모임 %d번 · 총 %s원".formatted(meetingCount, new DecimalFormat("#,##0").format(totalExpense)));
		addLine(lines, "최다 참석", topAttendees);
		addLine(lines, "가장 많이 간 곳", topPlace);
		addLine(lines, "벌칙왕", penaltyKings);
		return String.join("\n", lines);
	}

	private static void addLine(List<String> lines, String title, Ranked ranked) {
		if (!ranked.names().isEmpty()) {
			lines.add("%s: %s(%d번)".formatted(title, String.join(NAME_SEPARATOR, ranked.names()), ranked.count()));
		}
	}

	/**
	 * 한 줄에 들어갈 1위 이름들과 그 횟수. 이름이 없으면 그 줄을 뺀다.
	 */
	record Ranked(List<String> names, long count) {

		static final Ranked NONE = new Ranked(List.of(), 0);
	}
}
