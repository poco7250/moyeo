package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모임의 금액형·비무효 벌칙과 금액 조건을 쿼리 2번으로 읽는다. VOIDED·행동형 벌칙은 담지 않는다(PRD 4.5 무효 벌칙).
 */
@Component
@RequiredArgsConstructor
public class MeetingPenaltyLoader {

	private final PenaltyRepository penaltyRepository;
	private final PenaltyRuleRepository penaltyRuleRepository;

	/**
	 * 모임의 벌칙 묶음을 읽는다.
	 */
	public MeetingPenalties load(Long meetingId) {
		List<Penalty> penalties = penaltyRepository.findAllActiveMoneyByMeetingId(meetingId);
		if (penalties.isEmpty()) {
			return MeetingPenalties.empty();
		}
		Set<Long> gameIds = penalties.stream()
				.map(penalty -> penalty.getGameSession().getId())
				.collect(Collectors.toSet());
		Map<Long, PenaltyRule> rulesByGameId = penaltyRuleRepository.findAllWithTargetByGameSessionIdIn(gameIds)
				.stream()
				.collect(Collectors.toMap(rule -> rule.getGameSession().getId(), Function.identity()));
		return new MeetingPenalties(penalties, rulesByGameId);
	}
}
