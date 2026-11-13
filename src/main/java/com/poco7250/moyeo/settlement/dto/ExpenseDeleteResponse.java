package com.poco7250.moyeo.settlement.dto;

import java.util.List;

/**
 * 지출 삭제 응답. conditionResetGameIds가 비어 있지 않으면 화면에 "대상 지출이 삭제되어 조건을 다시 입력해야 함"을 보여준다.
 *
 * @param expenseId             삭제한 지출 id
 * @param conditionResetGameIds 이 지출을 대상으로 한 조건이 지워져 벌칙이 조건 미설정(ASSIGNED)으로 돌아간 게임 id
 */
public record ExpenseDeleteResponse(Long expenseId, List<Long> conditionResetGameIds) {
}
