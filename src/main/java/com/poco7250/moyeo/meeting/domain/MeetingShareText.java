package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.place.domain.Place;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * 모임 공유 문구(OG 미리보기·카카오톡 공유)를 만든다. 사용자가 입력한 값만 쓴다.
 */
public final class MeetingShareText {

	private static final String PLACE_UNDECIDED = "장소 미정";
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN);

	private MeetingShareText() {
	}

	/**
	 * seq 순으로 정렬된 차수에서 1차 확정 장소 이름을 찾는다. 확정 전이면 null이다.
	 */
	public static String firstPlaceLabel(List<Round> rounds) {
		if (rounds.isEmpty()) {
			return null;
		}
		Place place = rounds.getFirst().confirmedPlace();
		return place == null ? null : place.getLabel();
	}

	/**
	 * OG 미리보기 설명을 만든다. 예: "토요일 모임 · 강남 OO식당 · 5/8명 참석"
	 *
	 * @param attendCount 1차 참석(ATTEND) 인원
	 * @param memberCount 그룹 전체 멤버 수
	 */
	public static String ogDescription(LocalDateTime startAt, String firstPlaceLabel, long attendCount,
			long memberCount) {
		String weekday = startAt.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
		return "%s 모임 · %s · %d/%d명 참석".formatted(weekday, placeOrUndecided(firstPlaceLabel), attendCount, memberCount);
	}

	/**
	 * 단톡방에 붙여 넣을 카카오톡 공유 문구를 만든다.
	 */
	public static String kakaoText(String title, LocalDateTime startAt, String firstPlaceLabel, String url) {
		return "[%s]\n%s\n장소: %s\n%s".formatted(title, startAt.format(DATE_TIME), placeOrUndecided(firstPlaceLabel), url);
	}

	private static String placeOrUndecided(String label) {
		return label == null ? PLACE_UNDECIDED : label;
	}
}
