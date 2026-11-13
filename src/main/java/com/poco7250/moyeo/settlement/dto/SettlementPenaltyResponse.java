package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.settlement.domain.Expense;
import java.time.LocalDateTime;

/**
 * 정산 탭의 벌칙 반영 내역 1건(PRD S-4). "민수: 사다리 꽝 → 2차 볼링 +30,000원" 같은 문구는 이 값으로 화면에서 만든다.
 * ASSIGNED는 "조건 미설정"(rule·chargeAmount 없음), WAIVED는 "금액 벌칙 없음"(waivedBy·waivedAt)이다. VOIDED는 싣지 않는다.
 *
 * @param game         벌칙 출처 게임
 * @param rule         금액 조건. APPLIED가 아니면 null
 * @param chargeAmount 현재 계산한 부담액(원). APPLIED가 아니면 null
 * @param waivedBy     면제한 회원 id. WAIVED가 아니면 null
 * @param waivedAt     면제한 시각. WAIVED가 아니면 null
 */
public record SettlementPenaltyResponse(
		Long penaltyId,
		Long memberId,
		PenaltyStatus status,
		Game game,
		Rule rule,
		Long chargeAmount,
		Long waivedBy,
		LocalDateTime waivedAt) {

	/**
	 * 벌칙과 그 게임의 금액 조건으로 내역을 만든다.
	 *
	 * @param rule         게임의 금액 조건. 없으면 null
	 * @param chargeAmount 계산된 부담액. 계산에 들어가지 않았으면 null
	 */
	public static SettlementPenaltyResponse of(Penalty penalty, PenaltyRule rule, Long chargeAmount) {
		boolean applied = penalty.getStatus() == PenaltyStatus.APPLIED && rule != null;
		return new SettlementPenaltyResponse(penalty.getId(), penalty.getMember().getId(), penalty.getStatus(),
				Game.from(penalty.getGameSession()),
				applied ? Rule.from(rule) : null,
				applied ? chargeAmount : null,
				penalty.getWaivedBy(), penalty.getWaivedAt());
	}

	/**
	 * 벌칙 출처 게임.
	 */
	public record Game(Long gameId, GameType type, int roundSeq, String roundName) {

		static Game from(GameSession game) {
			return new Game(game.getId(), game.getType(), game.getRound().getSeq(), game.getRound().getName());
		}
	}

	/**
	 * 금액 조건.
	 *
	 * @param setBy 조건을 정한 회원 id
	 * @param setAt 조건을 정한 시각
	 */
	public record Rule(PenaltyMoneyType moneyType, Long amount, Integer ratioPct, TargetExpense targetExpense,
			Long setBy, LocalDateTime setAt) {

		static Rule from(PenaltyRule rule) {
			return new Rule(rule.getMoneyType(), rule.getAmount(), rule.getRatioPct(),
					TargetExpense.from(rule.getTargetExpense()), rule.getSetBy(), rule.getUpdatedAt());
		}
	}

	/**
	 * 조건의 대상 지출.
	 */
	public record TargetExpense(Long expenseId, int roundSeq, String roundName, String memo, long amount) {

		static TargetExpense from(Expense expense) {
			return new TargetExpense(expense.getId(), expense.getRound().getSeq(), expense.getRound().getName(),
					expense.getMemo(), expense.getAmount());
		}
	}
}
