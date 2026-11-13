package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산 이력의 리비전 1건(PRD S-11). 모두 확정 시점에 고정된 읽기 전용 기록이다.
 *
 * @param members        회원별 부담액·순잔액
 * @param changes        직전 리비전 대비 순잔액 변동액(0인 회원 포함). 리비전 1은 null
 * @param transfers      그 리비전의 송금 안내
 * @param penaltyCharges 그 리비전에 고정된 벌칙 부담액과 조건 스냅샷
 */
public record SettlementRevisionResponse(
		int revisionNo,
		Long confirmedBy,
		LocalDateTime confirmedAt,
		boolean warningsAcknowledged,
		List<SettlementMemberResponse> members,
		List<NetBalanceChangeResponse> changes,
		List<SettlementTransferResponse> transfers,
		List<RevisionPenaltyChargeResponse> penaltyCharges) {

	/**
	 * 리비전과 하위 스냅샷 응답을 묶는다.
	 */
	public static SettlementRevisionResponse of(SettlementRevision revision, List<SettlementMemberResponse> members,
			List<NetBalanceChangeResponse> changes, List<SettlementTransferResponse> transfers,
			List<RevisionPenaltyChargeResponse> penaltyCharges) {
		return new SettlementRevisionResponse(revision.getRevisionNo(), revision.getConfirmedBy().getId(),
				revision.getConfirmedAt(), revision.isWarningsAcknowledged(), members, changes, transfers,
				penaltyCharges);
	}
}
