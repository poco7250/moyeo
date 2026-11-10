package com.poco7250.moyeo.game.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 참가자. 게임을 만들 때 고른 차수 참석자로 확정된다.
 */
@Getter
@Entity
@Table(
		name = "game_participant",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_game_participant_game_session_id_member_id",
				columnNames = {"game_session_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameParticipant extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_session_id", nullable = false)
	private GameSession gameSession;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	private GameParticipant(GameSession gameSession, Member member) {
		this.gameSession = gameSession;
		this.member = member;
	}

	/**
	 * 게임 참가자를 만든다.
	 */
	public static GameParticipant create(GameSession gameSession, Member member) {
		return new GameParticipant(gameSession, member);
	}
}
