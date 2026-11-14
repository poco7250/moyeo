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
	private static final String DATE_VOTING = "날짜 투표 중";
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
	 * OG 미리보기 설명을 만든다. 예: "토요일 모임 · 강남 OO식당 · 5/8명 참석".
	 * 날짜 투표 중(startAt 없음)이면 요일 대신 "날짜 투표 중"을 넣는다.
	 *
	 * @param attendCount 1차 참석(ATTEND) 인원
	 * @param memberCount 그룹 전체 멤버 수
	 */
	public static String ogDescription(LocalDateTime startAt, String firstPlaceLabel, long attendCount,
			long memberCount) {
		String when = startAt == null ? DATE_VOTING
				: startAt.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN) + " 모임";
		return "%s · %s · %d/%d명 참석".formatted(when, placeOrUndecided(firstPlaceLabel), attendCount, memberCount);
	}

	/**
	 * 단톡방에 붙여 넣을 카카오톡 공유 문구를 만든다. 날짜 투표 중이면 날짜 자리에 "날짜 투표 중"을 넣는다.
	 */
	public static String kakaoText(String title, LocalDateTime startAt, String firstPlaceLabel, String url) {
		String when = startAt == null ? DATE_VOTING : startAt.format(DATE_TIME);
		return "[%s]\n%s\n장소: %s\n%s".formatted(title, when, placeOrUndecided(firstPlaceLabel), url);
	}

	/**
	 * 참석 미응답자 리마인드 문구를 만든다(PRD M-6). 예: "[토요일 모임] 3월 8일(토) 18:00\n아직 참석 응답 안 한 사람: 민수, 영희\n...".
	 * 날짜 투표 중이면 날짜 자리에 "날짜 투표 중", 응답 마감이 없으면 마감 문장 없이 "응답 부탁해!"를 넣는다.
	 *
	 * @param nicknames 미응답자 그룹 닉네임(비어 있지 않아야 한다)
	 */
	public static String reminderText(String title, LocalDateTime startAt, List<String> nicknames,
			LocalDateTime responseDeadline, String url) {
		String when = startAt == null ? DATE_VOTING : startAt.format(DATE_TIME);
		String ask = responseDeadline == null ? "응답 부탁해!"
				: responseDeadline.format(DATE_TIME) + "까지 응답 부탁해!";
		return "[%s] %s\n아직 참석 응답 안 한 사람: %s\n%s\n%s".formatted(title, when, String.join(", ", nicknames),
				ask, url);
	}

	private static String placeOrUndecided(String label) {
		return label == null ? PLACE_UNDECIDED : label;
	}
}
