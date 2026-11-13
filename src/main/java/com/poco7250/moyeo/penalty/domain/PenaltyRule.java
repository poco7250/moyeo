package com.poco7250.moyeo.penalty.domain;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.domain.Expense;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게임 후 호스트가 정하는 금액 조건. 게임당 1건이고 그 게임의 비무효 벌칙자 전원에게 각자 적용된다.
 * updatedAt은 조건을 정한 시각이라 자동 감사(BaseTimeEntity)를 쓰지 않는다. 다시 돌리기로 게임만 옮길 때 바뀌면 안 되기 때문이다.
 */
@Getter
@Entity
@Table(
		name = "penalty_rule",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_penalty_rule_game_session_id",
				columnNames = "game_session_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PenaltyRule {

	public static final int MIN_RATIO_PCT = 1;
	public static final int MAX_RATIO_PCT = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_session_id", nullable = false)
	private GameSession gameSession;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PenaltyMoneyType moneyType;

	// FIXED_AMOUNT일 때만 값이 있다(원)
	private Long amount;

	// RATIO일 때만 값이 있다(1~100 정수 %)
	private Integer ratioPct;

	// 같은 모임에 등록된 지출 1건
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_expense_id", nullable = false)
	private Expense targetExpense;

	// 조건을 정한 회원 id
	@Column(nullable = false)
	private Long setBy;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	private PenaltyRule(GameSession gameSession, PenaltyMoneyType moneyType, Long amount, Integer ratioPct,
			Expense targetExpense, Long setBy, LocalDateTime updatedAt) {
		this.gameSession = gameSession;
		this.moneyType = moneyType;
		this.amount = amount;
		this.ratioPct = ratioPct;
		this.targetExpense = targetExpense;
		this.setBy = setBy;
		this.updatedAt = updatedAt;
	}

	/**
	 * 게임의 금액 조건을 만든다. 종류에 맞는 값만 받는다(고정 금액은 amount, 비율은 ratioPct, 전액은 둘 다 없음).
	 *
	 * @throws BusinessException 종류와 값이 맞지 않거나 범위를 벗어나면 INVALID_INPUT
	 */
	public static PenaltyRule create(GameSession gameSession, PenaltyMoneyType moneyType, Long amount,
			Integer ratioPct, Expense targetExpense, Long setBy, LocalDateTime now) {
		requireValidValue(moneyType, amount, ratioPct);
		return new PenaltyRule(gameSession, moneyType, amount, ratioPct, targetExpense, setBy, now);
	}

	/**
	 * 조건을 다시 정한다. 새로 정한 것이므로 설정자와 설정 시각도 바꾼다.
	 *
	 * @throws BusinessException 종류와 값이 맞지 않거나 범위를 벗어나면 INVALID_INPUT
	 */
	public void change(PenaltyMoneyType moneyType, Long amount, Integer ratioPct, Expense targetExpense, Long setBy,
			LocalDateTime now) {
		requireValidValue(moneyType, amount, ratioPct);
		this.moneyType = moneyType;
		this.amount = amount;
		this.ratioPct = ratioPct;
		this.targetExpense = targetExpense;
		this.setBy = setBy;
		this.updatedAt = now;
	}

	/**
	 * 다시 돌린 새 회차로 조건을 옮긴다. 조건을 새로 정한 게 아니라서 설정자와 설정 시각은 그대로 둔다.
	 */
	public void moveTo(GameSession newGameSession) {
		this.gameSession = newGameSession;
	}

	private static void requireValidValue(PenaltyMoneyType moneyType, Long amount, Integer ratioPct) {
		boolean valid = switch (moneyType) {
			case FIXED_AMOUNT -> ratioPct == null && isValidAmount(amount);
			case RATIO -> amount == null && isValidRatio(ratioPct);
			case FULL -> amount == null && ratioPct == null;
		};
		if (!valid) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	private static boolean isValidAmount(Long amount) {
		return amount != null && amount >= Expense.MIN_AMOUNT && amount <= Expense.MAX_AMOUNT;
	}

	private static boolean isValidRatio(Integer ratioPct) {
		return ratioPct != null && ratioPct >= MIN_RATIO_PCT && ratioPct <= MAX_RATIO_PCT;
	}
}
