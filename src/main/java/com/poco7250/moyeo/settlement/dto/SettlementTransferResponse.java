package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.calc.TransferGuide;
import com.poco7250.moyeo.settlement.domain.Transfer;

/**
 * 송금 안내 1건 응답.
 */
public record SettlementTransferResponse(Long fromMemberId, Long toMemberId, long amount) {

	/**
	 * 현재 계산값을 응답으로 변환한다.
	 */
	public static SettlementTransferResponse from(TransferGuide transfer) {
		return new SettlementTransferResponse(transfer.fromMemberId(), transfer.toMemberId(), transfer.amount());
	}

	/**
	 * 저장된 송금 안내를 응답으로 변환한다.
	 */
	public static SettlementTransferResponse from(Transfer transfer) {
		return new SettlementTransferResponse(transfer.getFromMember().getId(), transfer.getToMember().getId(),
				transfer.getAmount());
	}
}
