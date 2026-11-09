package com.poco7250.moyeo.settlement.calc;

import java.util.List;

/**
 * 정산 계산 결과.
 *
 * @param members   memberId 오름차순 회원별 부담액·순잔액
 * @param transfers 송금 안내 목록
 * @param warnings  계산 경고 목록
 */
public record SettlementResult(
		List<MemberSettlement> members,
		List<TransferGuide> transfers,
		List<SettlementWarning> warnings) {
}
