package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleSnapshot;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지출 삭제 시 그 지출을 대상으로 한 벌칙 금액 조건을 지우고, 해당 게임의 APPLIED 벌칙을 ASSIGNED로 되돌린다(PRD S-1, 4.5).
 * 조건은 비무효 최신 회차에만 있어(다시 돌린 회차 공개 때 이관) 무효 회차를 가리키는 조건은 없다.
 * 지출 삭제와 같은 트랜잭션에서만 의미가 있어 MANDATORY로 둔다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TargetExpenseRuleDetacher {

	private final PenaltyRuleRepository penaltyRuleRepository;
	private final PenaltyRepository penaltyRepository;

	/**
	 * 지출을 대상으로 한 조건을 모두 지운다. 지출 삭제보다 먼저 불러야 조건의 지출 FK가 걸리지 않는다.
	 *
	 * @return 지운 조건 목록. 대상이 아니었으면 빈 목록
	 */
	public List<DetachedRule> detach(Long expenseId) {
		return penaltyRuleRepository.findAllByTargetExpenseId(expenseId).stream()
				.map(this::detachRule)
				.toList();
	}

	private DetachedRule detachRule(PenaltyRule rule) {
		Long gameId = rule.getGameSession().getId();
		PenaltyRuleSnapshot before = PenaltyRuleSnapshot.from(rule);
		penaltyRepository.findAllByGameSessionIdAndStatusNot(gameId, PenaltyStatus.VOIDED).stream()
				.filter(penalty -> penalty.getStatus() == PenaltyStatus.APPLIED)
				.forEach(Penalty::unapply);
		penaltyRuleRepository.delete(rule);
		return new DetachedRule(rule.getId(), gameId, before);
	}
}
