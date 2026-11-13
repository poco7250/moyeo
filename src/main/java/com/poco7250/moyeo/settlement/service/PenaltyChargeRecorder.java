package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.calc.PenaltyChargeResult;
import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.repository.PenaltyChargeRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 확정 리비전에 APPLIED 벌칙별 부담액을 스냅샷으로 남긴다(PRD S-10, N3). 부담액이 0원인 벌칙도 남긴다.
 */
@Component
@RequiredArgsConstructor
public class PenaltyChargeRecorder {

	private final PenaltyChargeRepository penaltyChargeRepository;

	/**
	 * 계산된 벌칙 부담액을 리비전에 저장한다.
	 *
	 * @param penalties 계산 입력을 만든 벌칙 묶음
	 * @param charges   그 입력으로 계산한 벌칙 부담액
	 * @throws IllegalStateException 계산 결과의 벌칙이나 조건이 묶음에 없으면(조립 버그)
	 */
	public List<PenaltyCharge> record(SettlementRevision revision, MeetingPenalties penalties,
			List<PenaltyChargeResult> charges) {
		Map<Long, Penalty> penaltyById = penalties.penalties().stream()
				.collect(Collectors.toMap(Penalty::getId, Function.identity()));
		return penaltyChargeRepository.saveAll(charges.stream()
				.map(charge -> toSnapshot(revision, penalties, penaltyById.get(charge.penaltyId()), charge))
				.toList());
	}

	private PenaltyCharge toSnapshot(SettlementRevision revision, MeetingPenalties penalties, Penalty penalty,
			PenaltyChargeResult charge) {
		if (penalty == null) {
			throw new IllegalStateException("계산 입력에 없는 벌칙: " + charge.penaltyId());
		}
		PenaltyRule rule = penalties.ruleOf(penalty.getGameSession().getId())
				.orElseThrow(() -> new IllegalStateException("조건 없는 벌칙: " + charge.penaltyId()));
		return PenaltyCharge.create(revision, penalty, charge.amount(), rule);
	}
}
