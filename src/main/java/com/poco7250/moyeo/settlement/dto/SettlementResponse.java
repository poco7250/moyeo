package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.calc.SettlementResult;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.domain.Transfer;
import java.util.List;

/**
 * 정산 조회·확정 응답.
 *
 * @param basisRevisionNo 송금 안내의 기준 리비전 번호. 미확정이면 null
 * @param preview         저장되지 않은 현재 계산값이면 true
 * @param warnings        미리보기의 계산 경고. 확정된 리비전에는 경고 내용을 저장하지 않아 빈 목록이다
 * @param penalties       벌칙 반영 내역(PRD S-4). 정산 상태와 상관없이 현재 벌칙 상태와 현재 계산 부담액이다
 */
public record SettlementResponse(
		SettlementStatus status,
		Integer basisRevisionNo,
		boolean preview,
		List<SettlementMemberResponse> members,
		List<SettlementTransferResponse> transfers,
		List<SettlementWarningResponse> warnings,
		List<SettlementPenaltyResponse> penalties) {

	/**
	 * 미확정 정산의 현재 계산값으로 미리보기 응답을 만든다.
	 */
	public static SettlementResponse preview(SettlementResult result, List<SettlementPenaltyResponse> penalties) {
		return new SettlementResponse(SettlementStatus.DRAFT, null, true,
				result.members().stream().map(SettlementMemberResponse::from).toList(),
				result.transfers().stream().map(SettlementTransferResponse::from).toList(),
				result.warnings().stream().map(SettlementWarningResponse::from).toList(),
				penalties);
	}

	/**
	 * 마지막 확정 리비전의 저장값으로 응답을 만든다.
	 */
	public static SettlementResponse confirmed(Settlement settlement, List<RevisionMemberShare> shares,
			List<Transfer> transfers, List<SettlementPenaltyResponse> penalties) {
		return new SettlementResponse(settlement.getStatus(), settlement.getCurrentRevisionNo(), false,
				shares.stream().map(SettlementMemberResponse::from).toList(),
				transfers.stream().map(SettlementTransferResponse::from).toList(),
				List.of(),
				penalties);
	}
}
