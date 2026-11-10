package com.poco7250.moyeo.game.domain;

/**
 * 게임 진행 상태. WAITING → PLAYING → REVEALED → CLOSED, WAITING·PLAYING → CANCELED로만 움직인다.
 */
public enum GameStatus {
	WAITING,
	PLAYING,
	REVEALED,
	CLOSED,
	CANCELED
}
