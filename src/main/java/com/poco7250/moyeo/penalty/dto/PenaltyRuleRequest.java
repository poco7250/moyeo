package com.poco7250.moyeo.penalty.dto;

import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.domain.Expense;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 벌칙 금액 조건 설정·변경 요청. 종류에 맞는 값만 보낸다(고정 금액은 amount, 비율은 ratioPct, 전액은 둘 다 없음).
 *
 * @param targetExpenseId 같은 모임에 등록된 지출 1건
 * @param amount 고정 금액(원). 1원~1억 원
 * @param ratioPct 비율(%). 1~100 정수
 */
public record PenaltyRuleRequest(
		@NotNull(message = "벌칙 금액 종류를 골라 주세요.")
		PenaltyMoneyType moneyType,

		@NotNull(message = "대상 지출을 골라 주세요.")
		Long targetExpenseId,

		Long amount,

		Integer ratioPct) {

	/**
	 * 종류에 맞는 값만 범위 안으로 들어왔는지 검사한다(필드 에러 이름: valueValid).
	 * 범위는 PenaltyRule 도메인 규칙과 같다. 종류가 없으면 moneyType 검증에 맡긴다.
	 */
	@AssertTrue(message = "고정 금액은 1원~1억 원, 비율은 1~100 정수만 입력할 수 있고, 전액은 값을 받지 않습니다.")
	public boolean isValueValid() {
		if (moneyType == null) {
			return true;
		}
		return switch (moneyType) {
			case FIXED_AMOUNT -> ratioPct == null && amount != null
					&& amount >= Expense.MIN_AMOUNT && amount <= Expense.MAX_AMOUNT;
			case RATIO -> amount == null && ratioPct != null
					&& ratioPct >= PenaltyRule.MIN_RATIO_PCT && ratioPct <= PenaltyRule.MAX_RATIO_PCT;
			case FULL -> amount == null && ratioPct == null;
		};
	}
}
