package com.poco7250.moyeo.settlement.domain;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
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
 * 리비전의 벌칙별 부담액 스냅샷(PRD 7장). 확정 시점의 조건·대상 지출 값을 복사해 두어, 이후 규칙이 바뀌거나
 * 대상 지출이 삭제돼도 그 리비전에서 "왜 이 금액인지"를 설명할 수 있다. 대상 지출은 삭제를 막지 않도록 FK가 아닌 값 컬럼이다.
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

	// 이 리비전에서 계산된 부담액(원). 앞 규칙이 잔액을 다 쓰면 0일 수 있다
	@Column(nullable = false)
	private long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PenaltyMoneyType moneyType;

	// FIXED_AMOUNT일 때만 값이 있다
	private Long fixedAmount;

	// RATIO일 때만 값이 있다
	private Integer ratioPct;

	@Column(nullable = false)
	private Long targetExpenseId;

	@Column(nullable = false)
	private long targetExpenseAmount;

	@Column(length = 100)
	private String targetExpenseMemo;

	private PenaltyCharge(SettlementRevision revision, Penalty penalty, long amount, PenaltyRule rule) {
		Expense target = rule.getTargetExpense();
		this.revision = revision;
		this.penalty = penalty;
		this.amount = amount;
		this.moneyType = rule.getMoneyType();
		this.fixedAmount = rule.getAmount();
		this.ratioPct = rule.getRatioPct();
		this.targetExpenseId = target.getId();
		this.targetExpenseAmount = target.getAmount();
		this.targetExpenseMemo = target.getMemo();
	}

	/**
	 * 확정 시점의 부담액과 금액 조건으로 스냅샷을 만든다.
	 *
	 * @param rule 벌칙 게임의 금액 조건(대상 지출이 로드돼 있어야 함)
	 */
	public static PenaltyCharge create(SettlementRevision revision, Penalty penalty, long amount, PenaltyRule rule) {
		return new PenaltyCharge(revision, penalty, amount, rule);
	}
}
