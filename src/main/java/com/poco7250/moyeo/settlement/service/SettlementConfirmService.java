package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.calc.SettlementCalculator;
import com.poco7250.moyeo.settlement.calc.SettlementResult;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.domain.Transfer;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import com.poco7250.moyeo.settlement.repository.TransferRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 최초 확정(PRD S-10). 확정 게이트를 통과하면 그 시점 계산값(멤버별 부담액·순잔액·벌칙 부담액·송금 안내)을
 * 리비전 1로 고정한다. 재확정은 Task 039에서 추가한다.
 * 정산 직렬화 대상이며 충돌 시 늦은 쪽은 409이고 재시도하지 않는다(PRD 6장 재시도 대상 아님).
 */
@Service
@RequiredArgsConstructor
public class SettlementConfirmService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final SettlementGuard settlementGuard;
	private final SettlementInputAssembler inputAssembler;
	private final MeetingPenaltyLoader meetingPenaltyLoader;
	private final SettlementConfirmGate confirmGate;
	private final PenaltyChargeRecorder penaltyChargeRecorder;
	private final SettlementRepository settlementRepository;
	private final SettlementRevisionRepository revisionRepository;
	private final RevisionMemberShareRepository memberShareRepository;
	private final TransferRepository transferRepository;
	private final MemberRepository memberRepository;
	private final Clock clock;

	/**
	 * 호스트가 미확정 정산을 확정한다. 검사에 실패하면 정산 상태를 바꾸지 않는다.
	 *
	 * @throws BusinessException 호스트가 아니면 NOT_MEETING_HOST, 이미 확정됐으면 SETTLEMENT_ALREADY_CONFIRMED,
	 *         조건 미설정 금액형 벌칙이 있으면 PENALTY_CONDITION_UNSET, 대기·진행 중 금액형 게임이 있으면
	 *         MONEY_GAME_IN_PROGRESS, 경고를 확인하지 않았으면 WARNING_UNCONFIRMED(모두 막힌 대상 목록을 상세에 싣는다)
	 */
	@Transactional
	public SettlementResponse confirm(Long meetingId, Long hostId, boolean acknowledgeWarnings) {
		meetingAccessChecker.requireHost(meetingId, hostId);
		Settlement settlement = settlementGuard.lockForAmountChange(meetingId);
		settlement.requireConfirmable();
		MeetingPenalties penalties = meetingPenaltyLoader.load(meetingId);
		SettlementResult result = SettlementCalculator.calculate(inputAssembler.assemble(meetingId, penalties));
		confirmGate.check(meetingId, penalties, result, acknowledgeWarnings);

		LocalDateTime now = LocalDateTime.now(clock);
		settlement.confirmFirst(now);
		// 리비전 INSERT의 FK 검사는 정산 행에 공유 잠금을 잡는다. 동시 확정끼리 커밋 때 UPDATE하면 데드락이 나므로
		// 정산 UPDATE를 먼저 내보내 늦은 쪽이 version 충돌(409)로 끝나게 한다
		settlementRepository.flush();
		SettlementRevision revision = revisionRepository.save(SettlementRevision.create(settlement,
				Settlement.FIRST_REVISION_NO, member(hostId), now, !result.warnings().isEmpty()));
		List<RevisionMemberShare> shares = saveMemberShares(revision, result);
		List<Transfer> transfers = saveTransfers(revision, result);
		penaltyChargeRecorder.record(revision, penalties, result.penaltyCharges());
		return SettlementResponse.confirmed(settlement, shares, transfers,
				penalties.toResponses(result.penaltyCharges()));
	}

	private List<RevisionMemberShare> saveMemberShares(SettlementRevision revision, SettlementResult result) {
		return memberShareRepository.saveAll(result.members().stream()
				.map(share -> RevisionMemberShare.create(revision, member(share.memberId()), share.shareAmount(),
						share.netBalance()))
				.toList());
	}

	private List<Transfer> saveTransfers(SettlementRevision revision, SettlementResult result) {
		return transferRepository.saveAll(result.transfers().stream()
				.map(transfer -> Transfer.create(revision, member(transfer.fromMemberId()),
						member(transfer.toMemberId()), transfer.amount()))
				.toList());
	}

	// 스냅샷의 FK만 필요해서 회원을 조회하지 않고 참조만 얻는다
	private Member member(Long memberId) {
		return memberRepository.getReferenceById(memberId);
	}
}
