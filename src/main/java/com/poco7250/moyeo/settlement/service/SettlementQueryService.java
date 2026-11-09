package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.calc.SettlementCalculator;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import com.poco7250.moyeo.settlement.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 조회(PRD S-5, S-10). 미확정이면 현재 계산값 미리보기, 확정 이후엔 마지막 확정 리비전의 저장값을 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class SettlementQueryService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final SettlementRepository settlementRepository;
	private final SettlementInputAssembler inputAssembler;
	private final SettlementRevisionRepository revisionRepository;
	private final RevisionMemberShareRepository memberShareRepository;
	private final TransferRepository transferRepository;

	/**
	 * 모임의 정산을 조회한다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 정산이 없으면 MEETING_NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public SettlementResponse get(Long meetingId, Long memberId) {
		meetingAccessChecker.requireMember(meetingId, memberId);
		Settlement settlement = settlementRepository.findByMeetingId(meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
		if (settlement.getStatus() == SettlementStatus.DRAFT) {
			return SettlementResponse.preview(SettlementCalculator.calculate(inputAssembler.assemble(meetingId)));
		}
		return savedRevision(settlement);
	}

	private SettlementResponse savedRevision(Settlement settlement) {
		SettlementRevision revision = revisionRepository
				.findBySettlementIdAndRevisionNo(settlement.getId(), settlement.getCurrentRevisionNo())
				.orElseThrow(() -> new IllegalStateException("확정된 정산에 리비전이 없습니다: " + settlement.getId()));
		return SettlementResponse.confirmed(settlement,
				memberShareRepository.findAllByRevisionIdOrderByMemberIdAsc(revision.getId()),
				transferRepository.findAllByRevisionIdOrderByIdAsc(revision.getId()));
	}
}
