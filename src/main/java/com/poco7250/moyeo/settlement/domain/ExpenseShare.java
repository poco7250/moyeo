package com.poco7250.moyeo.settlement.domain;

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
 * 지출의 회원별 분배 조정(제외·고정 금액·가중치). 지출당 1인 1건이며 Expense를 통해서만 만든다.
 * 한 사람당 제외·고정 금액·가중치 중 하나만 쓰는 규칙은 API 입력(ExpenseShareItem)에서 검사한다.
 */
@Getter
@Entity
@Table(
		uniqueConstraints = @UniqueConstraint(
				name = "uk_expense_share_expense_id_member_id",
				columnNames = {"expense_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseShare extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_id", nullable = false)
	private Expense expense;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	// 고정 금액(원). 없으면 null
	private Long amount;

	// 분배 가중치. 없으면 null
	private Integer weight;

	@Column(nullable = false)
	private boolean excluded;

	ExpenseShare(Expense expense, Member member, Long amount, Integer weight, boolean excluded) {
		this.expense = expense;
		this.member = member;
		this.amount = amount;
		this.weight = weight;
		this.excluded = excluded;
	}

	void update(Long amount, Integer weight, boolean excluded) {
		this.amount = amount;
		this.weight = weight;
		this.excluded = excluded;
	}
}
