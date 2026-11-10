package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseShareAdjustRequest;
import com.poco7250.moyeo.settlement.dto.ExpenseShareItem;
import com.poco7250.moyeo.settlement.dto.ExpenseSharesSnapshot;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지출의 개별 분배 조정(PRD S-3). 지출 쓰기와 같은 정산 직렬화·REOPENED·변경 로그 규칙을 따른다.
 * 조정 대상은 그룹 멤버면 되고, 참석자 기준 반영은 정산 계산에서 한다(참석 응답이 나중에 바뀌어도 조정값을 유지).
 */
@Service
@RequiredArgsConstructor
public class ExpenseShareService {

	private final ExpenseLoader expenseLoader;
	private final GroupMemberRepository groupMemberRepository;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;

	/**
	 * 지출의 분배 조정을 요청 목록으로 전체 교체한다.
	 *
	 * @throws BusinessException 없는 지출이면 EXPENSE_NOT_FOUND, 그룹 멤버가 아니면 NOT_GROUP_MEMBER,
	 *         조정 대상에 그룹 멤버가 아닌 회원이 있으면 INVALID_INPUT
	 */
	@Transactional
	public ExpenseResponse adjust(Long expenseId, Long memberId, ExpenseShareAdjustRequest request) {
		Expense expense = expenseLoader.loadForMember(expenseId, memberId);
		Meeting meeting = expense.getRound().getMeeting();
		settlementGuard.lockForAmountChange(meeting.getId());
		Map<Long, Member> targets = findTargets(meeting.getGroup().getId(), request.shares());
		ExpenseSharesSnapshot before = ExpenseSharesSnapshot.from(expense);
		replaceShares(expense, request.shares(), targets);
		settlementGuard.markAmountChanged(meeting.getId(), memberId,
				List.of(new SettlementChangeSummary(ChangeLogEntityType.EXPENSE_SHARE, expense.getId(), ChangeType.UPDATE)));
		changeLogRecorder.record(meeting.getId(), memberId, ChangeLogEntityType.EXPENSE_SHARE, expense.getId(),
				ChangeType.UPDATE, before, ExpenseSharesSnapshot.from(expense));
		return ExpenseResponse.from(expense);
	}

	private Map<Long, Member> findTargets(Long groupId, List<ExpenseShareItem> items) {
		if (items.isEmpty()) {
			return Map.of();
		}
		List<Long> memberIds = items.stream().map(ExpenseShareItem::memberId).toList();
		List<GroupMember> found = groupMemberRepository.findAllByGroupIdAndMemberIdIn(groupId, memberIds);
		if (found.size() != memberIds.size()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return found.stream().map(GroupMember::getMember).collect(Collectors.toMap(Member::getId, member -> member));
	}

	private void replaceShares(Expense expense, List<ExpenseShareItem> items, Map<Long, Member> targets) {
		expense.removeSharesExcept(targets.keySet());
		items.forEach(item -> expense.putShare(targets.get(item.memberId()), item.amount(), item.weight(),
				item.excludedOrFalse()));
	}
}
