package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.calc.PenaltyChargeResult;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 모임의 금액형·비무효 벌칙과 그 게임들의 금액 조건 묶음. 정산 계산 입력과 벌칙 반영 내역이 같은 값을 쓴다.
 *
 * @param penalties     게임 순번 → 원 회차 id → 벌칙 id 순 벌칙
 * @param rulesByGameId 게임 id별 금액 조건. 조건이 없는 게임은 키가 없다
 */
public record MeetingPenalties(List<Penalty> penalties, Map<Long, PenaltyRule> rulesByGameId) {

	/**
	 * 벌칙이 없는 묶음.
	 */
	public static MeetingPenalties empty() {
		return new MeetingPenalties(List.of(), Map.of());
	}

	/**
	 * 게임의 금액 조건을 찾는다.
	 */
	public Optional<PenaltyRule> ruleOf(Long gameId) {
		return Optional.ofNullable(rulesByGameId.get(gameId));
	}

	/**
	 * 계산된 벌칙 부담액을 붙여 벌칙 반영 내역(PRD S-4)을 만든다. 순서는 벌칙 조회 순서(게임 순번 순)다.
	 */
	public List<SettlementPenaltyResponse> toResponses(List<PenaltyChargeResult> charges) {
		Map<Long, Long> chargeByPenaltyId = charges.stream()
				.collect(Collectors.toMap(PenaltyChargeResult::penaltyId, PenaltyChargeResult::amount, Long::sum));
		return penalties.stream()
				.map(penalty -> SettlementPenaltyResponse.of(penalty,
						rulesByGameId.get(penalty.getGameSession().getId()),
						chargeByPenaltyId.get(penalty.getId())))
				.toList();
	}
}
