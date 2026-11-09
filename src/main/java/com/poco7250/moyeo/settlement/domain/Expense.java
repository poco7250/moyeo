package com.poco7250.moyeo.settlement.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차수의 지출. 금액은 원 단위 정수이고, 분배 조정은 ExpenseShare로 따로 둔다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense extends BaseTimeEntity {

	public static final long MIN_AMOUNT = 1L;
	public static final long MAX_AMOUNT = 100_000_000L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false)
	private Round round;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payer_id", nullable = false)
	private Member payer;

	@Column(nullable = false)
	private long amount;

	@Column(length = 100)
	private String memo;

	@OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ExpenseShare> shares = new ArrayList<>();

	private Expense(Round round, Member payer, long amount, String memo) {
		this.round = round;
		this.payer = payer;
		this.amount = amount;
		this.memo = memo;
	}

	/**
	 * 차수에 지출을 만든다.
	 *
	 * @throws BusinessException 금액이 1원~1억 원을 벗어나면 INVALID_INPUT
	 */
	public static Expense create(Round round, Member payer, long amount, String memo) {
		requireValidAmount(amount);
		return new Expense(round, payer, amount, memo);
	}

	/**
	 * 회원의 분배 조정을 추가한다. 고정 금액·가중치는 없으면 null이다.
	 *
	 * @throws BusinessException 고정 금액이 1원~1억 원을 벗어나면 INVALID_INPUT
	 */
	public ExpenseShare addShare(Member member, Long fixedAmount, Integer weight, boolean excluded) {
		requireValidFixedAmount(fixedAmount);
		ExpenseShare share = new ExpenseShare(this, member, fixedAmount, weight, excluded);
		shares.add(share);
		return share;
	}

	/**
	 * 결제자·금액·메모를 바꾼다. 분배 조정은 그대로 둔다.
	 *
	 * @throws BusinessException 금액이 1원~1억 원을 벗어나면 INVALID_INPUT
	 */
	public void update(Member payer, long amount, String memo) {
		requireValidAmount(amount);
		this.payer = payer;
		this.amount = amount;
		this.memo = memo;
	}

	/**
	 * 회원의 분배 조정을 저장한다. 이미 있으면 그 행을 고친다.
	 * 지우고 다시 넣으면 Hibernate가 INSERT를 먼저 실행해 (expense, member) 유니크를 위반하기 때문이다.
	 *
	 * @throws BusinessException 고정 금액이 1원~1억 원을 벗어나면 INVALID_INPUT
	 */
	public void putShare(Member member, Long fixedAmount, Integer weight, boolean excluded) {
		Optional<ExpenseShare> existing = shares.stream()
				.filter(share -> share.getMember().getId().equals(member.getId()))
				.findFirst();
		if (existing.isEmpty()) {
			addShare(member, fixedAmount, weight, excluded);
			return;
		}
		requireValidFixedAmount(fixedAmount);
		existing.get().update(fixedAmount, weight, excluded);
	}

	/**
	 * 주어진 회원을 뺀 나머지 분배 조정을 지운다.
	 */
	public void removeSharesExcept(Set<Long> memberIds) {
		shares.removeIf(share -> !memberIds.contains(share.getMember().getId()));
	}

	private static void requireValidFixedAmount(Long fixedAmount) {
		if (fixedAmount != null) {
			requireValidAmount(fixedAmount);
		}
	}

	private static void requireValidAmount(long amount) {
		if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}
}
