package com.poco7250.moyeo.meeting.domain;

import java.time.LocalDateTime;

/**
 * 모임 웹 푸시 알림 문구(Task 056). 날짜 표기는 카톡 공유 문구({@link MeetingShareText})와 같다.
 */
public final class MeetingPushText {

	private MeetingPushText() {
	}

	/**
	 * 새 모임 알림 본문. 날짜 투표 중이면 날짜 고르기를, 아니면 참석 응답을 부탁한다.
	 */
	public static String meetingCreatedBody(LocalDateTime startAt) {
		return startAt == null ? "날짜 투표 중이야. 되는 날짜를 골라 줘!"
				: startAt.format(MeetingShareText.DATE_TIME) + " · 참석 응답 부탁해!";
	}

	/**
	 * 날짜 확정 알림 본문.
	 */
	public static String dateConfirmedBody(LocalDateTime startAt) {
		return startAt.format(MeetingShareText.DATE_TIME) + "로 정해졌어. 참석 응답 부탁해!";
	}

	/**
	 * 장소 확정 알림 본문. 예: "1차 장소: 강남 OO식당".
	 */
	public static String placeConfirmedBody(String roundName, String placeLabel) {
		return "%s 장소: %s".formatted(roundName, placeLabel);
	}

	/**
	 * 리마인드 알림 본문. 응답 마감이 없으면 마감 문장 없이 부탁만 한다.
	 */
	public static String reminderBody(LocalDateTime responseDeadline) {
		return responseDeadline == null ? "아직 참석 응답을 안 했어. 응답 부탁해!"
				: "아직 참석 응답을 안 했어. " + responseDeadline.format(MeetingShareText.DATE_TIME) + "까지 응답 부탁해!";
	}
}
