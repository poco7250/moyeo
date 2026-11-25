package com.poco7250.moyeo.settlement.domain;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 리비전의 벌칙별 부담액 스냅샷(PRD 7장). 확정 시점의 게임 금액을 복사해 두어, 이후 값이 바뀌어도
 * 그 리비전에서 "왜 이 금액인지"를 설명할 수 있다. 2026-11-24 전 리비전은 비율·대상 지출 조건 값도 남아 있다
 * (대상 지출은 삭제를 막지 않도록 FK가 아닌 값 컬럼). 새 스냅샷은 고정 금액(FIXED_AMOUNT)만 쓴다.
 */
@Getter
@Entity
@Immutable
@Table(
		uniqueConstraints = @UniqueConstraint(
				name = "uk_penalty_charge_revision_id_penalty_id",
				columnNames = {"revision_id", "penalty_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PenaltyCharge {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "revision_id", nullable = false)
	private SettlementRevision revision;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "penalty_id", nullable = false)
	private Penalty penalty;

	// 이 리비전에서 계산된 부담액(원). 나눠 받을 사람이 없으면 0일 수 있다
	@Column(nullable = false)
	private long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PenaltyMoneyType moneyType;

	// FIXED_AMOUNT일 때만 값이 있다(새 스냅샷은 게임 금액)
	private Long fixedAmount;

	// 옛 RATIO 조건일 때만 값이 있다
	private Integer ratioPct;

	// 옛 조건의 대상 지출. 새 스냅샷은 비어 있다
	private Long targetExpenseId;

	private Long targetExpenseAmount;

	@Column(length = 100)
	private String targetExpenseMemo;

	private PenaltyCharge(SettlementRevision revision, Penalty penalty, long amount, long gameAmount) {
		this.revision = revision;
		this.penalty = penalty;
		this.amount = amount;
		this.moneyType = PenaltyMoneyType.FIXED_AMOUNT;
		this.fixedAmount = gameAmount;
	}

	/**
	 * 확정 시점의 부담액과 게임 금액으로 스냅샷을 만든다.
	 *
	 * @param gameAmount 벌칙 게임의 1인 금액(원)
	 */
	public static PenaltyCharge create(SettlementRevision revision, Penalty penalty, long amount, long gameAmount) {
		return new PenaltyCharge(revision, penalty, amount, gameAmount);
	}
}
