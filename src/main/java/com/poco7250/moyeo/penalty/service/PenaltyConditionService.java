package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyWaiverSnapshot;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 금액형 게임 벌칙의 면제·면제 취소. 모임 정산 권한(호스트·방장·총무)만 할 수 있다.
 * 금액은 게임을 만들 때 정해지므로(PRD R-4, 2026-11-24) 여기서는 반영 여부만 바꾼다.
 * 면제는 게임 단위라 그 게임의 비무효 벌칙 전원이 함께 바뀐다.
 * 둘 다 금액 영향 변경이라 정산 직렬화 대상이며, 확정된 정산이면 REOPENED로 되돌리고 변경 로그를 남긴다.
 * 충돌 시 늦은 쪽은 409이고 재시도하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PenaltyConditionService {

	private final PenaltyConditionLoader penaltyConditionLoader;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;
	private final Clock clock;

	/**
	 * 금액 벌칙을 전원 면제한다(APPLIED → WAIVED).
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException 정산 권한이 없으면 NOT_MEETING_HOST,
	 *         면제할 수 없는 게임이거나 이미 면제 상태면 PENALTY_NOT_CONFIGURABLE
	 */
	@Transactional
	public PenaltyConditionResponse waive(Long gameId, Long memberId) {
		PenaltyConditionTarget target = penaltyConditionLoader.loadForSettlementManager(gameId, memberId);
		PenaltyWaiverSnapshot before = target.notWaivedSnapshot();
		LocalDateTime now = LocalDateTime.now(clock);
		target.penalties().forEach(penalty -> penalty.waive(memberId, now));
		reflect(target, memberId, before, target.waivedSnapshot());
		return target.toResponse();
	}

	/**
	 * 면제를 취소해 다시 정산에 반영한다(WAIVED → APPLIED).
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException 정산 권한이 없으면 NOT_MEETING_HOST,
	 *         면제 상태가 아니면 PENALTY_NOT_CONFIGURABLE
	 */
	@Transactional
	public PenaltyConditionResponse unwaive(Long gameId, Long memberId) {
		PenaltyConditionTarget target = penaltyConditionLoader.loadForSettlementManager(gameId, memberId);
		PenaltyWaiverSnapshot before = target.waivedSnapshot();
		target.penalties().forEach(Penalty::unwaive);
		reflect(target, memberId, before, target.notWaivedSnapshot());
		return target.toResponse();
	}

	/**
	 * 변경을 정산에 반영하고 변경 로그를 남긴다. 로그가 변경 후 정산 상태를 담으므로 REOPENED 전이를 먼저 한다.
	 */
	private void reflect(PenaltyConditionTarget target, Long actorId, PenaltyWaiverSnapshot before,
			PenaltyWaiverSnapshot after) {
		settlementGuard.markAmountChanged(target.meetingId(), actorId,
				List.of(new SettlementChangeSummary(ChangeLogEntityType.PENALTY, target.gameId(), ChangeType.UPDATE)));
		changeLogRecorder.record(target.meetingId(), actorId, ChangeLogEntityType.PENALTY, target.gameId(),
				ChangeType.UPDATE, before, after);
	}
}
