package com.poco7250.moyeo.settlement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 지출 등록·수정 요청. 범위는 Expense 도메인 규칙(1원~1억 원, 메모 100자)과 같다.
 */
public record ExpenseRequest(
		@NotNull(message = "금액을 입력해 주세요.")
		@Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
		@Max(value = 100_000_000, message = "금액은 1억 원 이하여야 합니다.")
		Long amount,

		@NotNull(message = "결제자를 골라 주세요.")
		Long payerId,

		@Size(max = 100, message = "메모는 100자 이내로 입력해 주세요.")
		String memo) {
}
