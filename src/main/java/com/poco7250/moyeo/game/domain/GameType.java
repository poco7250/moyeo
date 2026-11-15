package com.poco7250.moyeo.game.domain;

/**
 * 게임 종류. 사다리·꽝뽑기·룰렛은 벌칙자를 뽑고(차이는 화면 연출뿐), 순서 정하기는 참가자 전원의 순서를 정한다.
 */
public enum GameType {
	LADDER,
	DRAW,
	ROULETTE,
	// 참가자 전원의 순서(1~N번)를 정한다. 벌칙은 없다
	ORDER;

	/**
	 * 벌칙자 대신 참가자 전원의 순서를 정하는 종류인지 확인한다.
	 */
	public boolean isOrder() {
		return this == ORDER;
	}
}
