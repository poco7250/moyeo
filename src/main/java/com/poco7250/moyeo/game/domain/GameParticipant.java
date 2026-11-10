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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 참가자. 꽝뽑기에서 고른 카드 번호를 함께 갖고, 같은 카드는 유니크 제약으로 한 명만 가져간다.
 */
@Getter
@Entity
@Table(
		name = "game_participant",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_game_participant_game_session_id_member_id",
						columnNames = {"game_session_id", "member_id"}),
				@UniqueConstraint(
						name = "uk_game_participant_game_session_id_picked_slot",
						columnNames = {"game_session_id", "picked_slot"})
		})
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

	@Column(nullable = false)
	private boolean ready;

	@Column(name = "picked_slot")
	private Integer pickedSlot;

	private GameParticipant(GameSession gameSession, Member member) {
		this.gameSession = gameSession;
		this.member = member;
	}

	/**
	 * 게임 참가자를 준비 전 상태로 만든다.
	 */
	public static GameParticipant create(GameSession gameSession, Member member) {
		return new GameParticipant(gameSession, member);
	}

	/**
	 * 고른 카드 번호를 기록한다.
	 */
	public void pick(int slot) {
		this.pickedSlot = slot;
	}
}
