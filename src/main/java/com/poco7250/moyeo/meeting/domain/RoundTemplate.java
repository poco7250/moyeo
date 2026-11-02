package com.poco7250.moyeo.meeting.domain;

import java.util.List;

/**
 * 모임 생성 시 고를 수 있는 차수 템플릿(PRD M-2). 목록은 2026-11-02 결정.
 * 템플릿에 없는 구성은 요청의 rounds(차수 이름 목록)로 직접 만든다.
 */
public enum RoundTemplate {

	MEAL_ONLY("밥"),
	MEAL_BOWLING("밥", "볼링"),
	MEAL_BOARD_GAME("밥", "보드게임"),
	MEAL_BOWLING_BOARD_GAME("밥", "볼링", "보드게임");

	private final List<String> roundNames;

	RoundTemplate(String... roundNames) {
		this.roundNames = List.of(roundNames);
	}

	/**
	 * 1차부터 순서대로 차수 이름 목록을 돌려준다.
	 */
	public List<String> roundNames() {
		return roundNames;
	}
}
