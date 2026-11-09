package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지출을 조회하고 요청자가 그 모임의 그룹 멤버인지 검사한다. 지출 쓰기 Service들이 진입 시 공통으로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ExpenseLoader {

	private final ExpenseRepository expenseRepository;
	private final MeetingAccessChecker meetingAccessChecker;

	/**
	 * 지출을 조회하고 인가를 검사한다.
	 *
	 * @throws BusinessException 없는 지출이면 EXPENSE_NOT_FOUND, 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	public Expense loadForMember(Long expenseId, Long memberId) {
		Expense expense = expenseRepository.findWithRoundById(expenseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));
		meetingAccessChecker.requireMember(expense.getRound().getMeeting().getId(), memberId);
		return expense;
	}
}
