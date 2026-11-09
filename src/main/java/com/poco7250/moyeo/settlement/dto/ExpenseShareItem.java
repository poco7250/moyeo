package com.poco7250.moyeo.settlement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 회원 한 명의 분배 조정. 제외(excluded=true)·고정 금액(amount)·가중치(weight) 중 정확히 하나만 보낸다.
 */
public record ExpenseShareItem(
		@NotNull(message = "조정할 멤버를 골라 주세요.")
		Long memberId,

		@Min(value = 1, message = "고정 금액은 1원 이상이어야 합니다.")
		@Max(value = 100_000_000, message = "고정 금액은 1억 원 이하여야 합니다.")
		Long amount,

		@Min(value = 1, message = "가중치는 1 이상이어야 합니다.")
		@Max(value = 100, message = "가중치는 100 이하여야 합니다.")
		Integer weight,

		Boolean excluded) {

	/**
	 * 조정 종류가 정확히 하나인지 검사한다(필드 에러 이름: singleAdjustment).
	 */
	@AssertTrue(message = "제외, 고정 금액, 가중치 중 하나만 골라 주세요.")
	public boolean isSingleAdjustment() {
		int kinds = (excludedOrFalse() ? 1 : 0) + (amount != null ? 1 : 0) + (weight != null ? 1 : 0);
		return kinds == 1;
	}

	/**
	 * 보내지 않으면 제외가 아닌 것으로 본다.
	 */
	public boolean excludedOrFalse() {
		return Boolean.TRUE.equals(excluded);
	}
}
