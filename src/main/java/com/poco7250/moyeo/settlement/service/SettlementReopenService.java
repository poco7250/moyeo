package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementStatusSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 확정 되돌리기(Task 075). 호스트 권한자(호스트·방장)가 확정된 정산을 재확정 필요(REOPENED)로 되돌린다.
 * 확정 리비전·송금 안내는 지우지 않아 재확정하면 리비전 +1과 직전 리비전 대비 변동액이 나온다.
 * 확정·금액 영향 쓰기와 같은 정산 잠금으로 직렬화하고, 충돌하면 늦은 쪽이 409다.
 */
@Service
@RequiredArgsConstructor
public class SettlementReopenService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;
	private final SettlementQueryService settlementQueryService;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 확정된 정산을 REOPENED로 되돌리고 변경 로그·"재확정 필요" 이벤트를 남긴다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 호스트 권한이 없으면(총무 포함) NOT_MEETING_HOST,
	 *         CONFIRMED가 아니면 SETTLEMENT_NOT_CONFIRMED
	 */
	@Transactional
	public SettlementResponse reopen(Long meetingId, Long hostId) {
		meetingAccessChecker.requireHost(meetingId, hostId);
		Settlement settlement = settlementGuard.lockForAmountChange(meetingId);
		settlement.requireReopenable();
		Integer revisionNo = settlement.getCurrentRevisionNo();
		settlement.reopen();
		changeLogRecorder.record(meetingId, hostId, ChangeLogEntityType.SETTLEMENT, settlement.getId(),
				ChangeType.UPDATE, new SettlementStatusSnapshot(SettlementStatus.CONFIRMED, revisionNo),
				new SettlementStatusSnapshot(SettlementStatus.REOPENED, revisionNo));
		SettlementChangeSummary change = new SettlementChangeSummary(ChangeLogEntityType.SETTLEMENT,
				settlement.getId(), ChangeType.UPDATE);
		eventPublisher.publishEvent(
				new SettlementReopenedEvent(meetingId, new SettlementReopenedResponse(hostId, List.of(change))));
		return settlementQueryService.get(meetingId, hostId);
	}
}
