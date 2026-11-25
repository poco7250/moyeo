package com.poco7250.moyeo.penalty.domain;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 결과로 정해진 벌칙자 한 명의 벌칙. 금액은 게임(GameSession.penaltyAmount)에 있고, 정산을 계산할 때마다 반영한다.
 */
@Getter
@Entity
@Table(
		name = "penalty",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_penalty_game_session_id_member_id",
				columnNames = {"game_session_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Penalty extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_session_id", nullable = false)
	private GameSession gameSession;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PenaltyStatus status;

	// 면제 처리한 회원 id. 면제 상태가 아니면 null
	private Long waivedBy;

	private LocalDateTime waivedAt;

	@Version
	private Long version;

	private Penalty(GameSession gameSession, Member member) {
		this.gameSession = gameSession;
		this.member = member;
		this.status = gameSession.chargesMoney() ? PenaltyStatus.APPLIED : PenaltyStatus.ASSIGNED;
	}

	/**
	 * 벌칙자를 확정한다. 금액이 정해진 금액형 게임이면 바로 정산에 반영되는 APPLIED,
	 * 행동형·벌칙 없음(금액 없음)이면 ASSIGNED로 만든다.
	 */
	public static Penalty assign(GameSession gameSession, Member member) {
		return new Penalty(gameSession, member);
	}

	/**
	 * 금액 벌칙을 면제한다(APPLIED → WAIVED).
	 *
	 * @throws BusinessException APPLIED가 아니면 PENALTY_NOT_CONFIGURABLE
	 */
	public void waive(Long waivedBy, LocalDateTime waivedAt) {
		requireStatus(PenaltyStatus.APPLIED);
		this.status = PenaltyStatus.WAIVED;
		this.waivedBy = waivedBy;
		this.waivedAt = waivedAt;
	}

	/**
	 * 면제를 취소해 다시 정산에 반영한다(WAIVED → APPLIED).
	 *
	 * @throws BusinessException WAIVED가 아니면 PENALTY_NOT_CONFIGURABLE
	 */
	public void unwaive() {
		requireStatus(PenaltyStatus.WAIVED);
		this.status = PenaltyStatus.APPLIED;
		clearWaiver();
	}

	/**
	 * 다시 돌리기로 무효 처리한다(ASSIGNED·APPLIED·WAIVED → VOIDED). 면제 정보는 이력으로 남긴다.
	 *
	 * @throws BusinessException 이미 VOIDED면 PENALTY_NOT_CONFIGURABLE
	 */
	public void voidOut() {
		requireStatus(PenaltyStatus.ASSIGNED, PenaltyStatus.APPLIED, PenaltyStatus.WAIVED);
		this.status = PenaltyStatus.VOIDED;
	}

	/**
	 * 다시 돌리기로 무효가 된 벌칙인지 확인한다.
	 */
	public boolean isVoided() {
		return status == PenaltyStatus.VOIDED;
	}

	private void clearWaiver() {
		this.waivedBy = null;
		this.waivedAt = null;
	}

	private void requireStatus(PenaltyStatus... allowed) {
		for (PenaltyStatus candidate : allowed) {
			if (status == candidate) {
				return;
			}
		}
		throw new BusinessException(ErrorCode.PENALTY_NOT_CONFIGURABLE);
	}
}
