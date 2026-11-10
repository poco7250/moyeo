package com.poco7250.moyeo.game.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 참가자별 게임 결과(예: 꽝, 당첨).
 */
@Getter
@Entity
@Table(name = "game_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameResult extends BaseTimeEntity {

	/** 벌칙자로 뽑힌 참가자의 결과 */
	public static final String OUTCOME_PENALTY = "PENALTY";

	/** 벌칙을 피한 참가자의 결과 */
	public static final String OUTCOME_SAFE = "SAFE";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_session_id", nullable = false)
	private GameSession gameSession;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false, length = 50)
	private String outcome;

	private GameResult(GameSession gameSession, Member member, String outcome) {
		this.gameSession = gameSession;
		this.member = member;
		this.outcome = outcome;
	}

	/**
	 * 참가자의 게임 결과를 만든다.
	 */
	public static GameResult create(GameSession gameSession, Member member, String outcome) {
		return new GameResult(gameSession, member, outcome);
	}
}
