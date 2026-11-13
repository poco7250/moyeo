package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.calc.NetBalanceDeltaCalculator;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.dto.NetBalanceChangeResponse;
import com.poco7250.moyeo.settlement.dto.RevisionPenaltyChargeResponse;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementRevisionResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.repository.PenaltyChargeRepository;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import com.poco7250.moyeo.settlement.repository.TransferRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 이력(리비전 목록) 조회(PRD S-11). 리비전 수와 상관없이 쿼리 수가 고정되도록 하위 스냅샷을 한 번에 읽고,
 * 직전 리비전 대비 변동액은 저장하지 않고 인접 리비전끼리 파생한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementHistoryService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final SettlementRepository settlementRepository;
	private final SettlementRevisionRepository revisionRepository;
	private final RevisionMemberShareRepository memberShareRepository;
	private final TransferRepository transferRepository;
	private final PenaltyChargeRepository penaltyChargeRepository;

	/**
	 * 모임 정산의 리비전을 번호 순으로 돌려준다. 확정 전이면 빈 목록이다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 정산이 없으면 MEETING_NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public List<SettlementRevisionResponse> revisions(Long meetingId, Long memberId) {
		meetingAccessChecker.requireMember(meetingId, memberId);
		Settlement settlement = settlementRepository.findByMeetingId(meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
		List<SettlementRevision> revisions = revisionRepository
				.findAllBySettlementIdOrderByRevisionNoAsc(settlement.getId());
		if (revisions.isEmpty()) {
			return List.of();
		}
		RevisionSnapshots snapshots = loadSnapshots(revisions);
		List<SettlementRevisionResponse> responses = new ArrayList<>(revisions.size());
		List<SettlementMemberResponse> previousMembers = null;
		for (SettlementRevision revision : revisions) {
			List<SettlementMemberResponse> members = snapshots.members(revision.getId());
			responses.add(SettlementRevisionResponse.of(revision, members, changes(previousMembers, members),
					snapshots.transfers(revision.getId()), snapshots.penaltyCharges(revision.getId())));
			previousMembers = members;
		}
		return responses;
	}

	private RevisionSnapshots loadSnapshots(List<SettlementRevision> revisions) {
		List<Long> revisionIds = revisions.stream().map(SettlementRevision::getId).toList();
		return new RevisionSnapshots(
				memberShareRepository.findAllByRevisionIdInOrderByMemberIdAsc(revisionIds).stream()
						.collect(Collectors.groupingBy(share -> share.getRevision().getId(),
								Collectors.mapping(SettlementMemberResponse::from, Collectors.toList()))),
				transferRepository.findAllByRevisionIdInOrderByIdAsc(revisionIds).stream()
						.collect(Collectors.groupingBy(transfer -> transfer.getRevision().getId(),
								Collectors.mapping(SettlementTransferResponse::from, Collectors.toList()))),
				penaltyChargeRepository.findAllWithPenaltyByRevisionIdIn(revisionIds).stream()
						.collect(Collectors.groupingBy(charge -> charge.getRevision().getId(),
								Collectors.mapping(RevisionPenaltyChargeResponse::from, Collectors.toList()))));
	}

	// 리비전 1(직전 없음)은 null
	private List<NetBalanceChangeResponse> changes(List<SettlementMemberResponse> previous,
			List<SettlementMemberResponse> current) {
		if (previous == null) {
			return null;
		}
		return NetBalanceDeltaCalculator.calculate(netBalances(previous), netBalances(current)).stream()
				.map(NetBalanceChangeResponse::from)
				.toList();
	}

	private Map<Long, Long> netBalances(List<SettlementMemberResponse> members) {
		return members.stream().collect(Collectors.toMap(SettlementMemberResponse::memberId,
				SettlementMemberResponse::netBalance));
	}

	// 리비전 id별로 묶은 하위 스냅샷 응답. 행이 없는 리비전은 빈 목록
	private record RevisionSnapshots(
			Map<Long, List<SettlementMemberResponse>> members,
			Map<Long, List<SettlementTransferResponse>> transfers,
			Map<Long, List<RevisionPenaltyChargeResponse>> penaltyCharges) {

		List<SettlementMemberResponse> members(Long revisionId) {
			return members.getOrDefault(revisionId, List.of());
		}

		List<SettlementTransferResponse> transfers(Long revisionId) {
			return transfers.getOrDefault(revisionId, List.of());
		}

		List<RevisionPenaltyChargeResponse> penaltyCharges(Long revisionId) {
			return penaltyCharges.getOrDefault(revisionId, List.of());
		}
	}
}
