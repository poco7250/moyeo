package com.poco7250.moyeo.settlement.dto;

import java.util.List;

/**
 * "정산이 바뀌었어요(리비전 n)" 실시간 이벤트 본문(PRD 6장 알림(재확정)).
 *
 * @param confirmedBy 재확정한 호스트
 * @param changes     직전 확정 리비전 대비 변동액이 0이 아닌 회원만
 * @param transfers   새 리비전의 전체 송금 안내
 * @param notice      고정 안내 문구
 * @param shareText   호스트용 공유 문구
 */
public record SettlementReconfirmedResponse(int revisionNo, Long confirmedBy, List<NetBalanceChangeResponse> changes,
		List<SettlementTransferResponse> transfers, String notice, String shareText) {

	/**
	 * 재확정 응답의 변동액 안내에서 변동액이 0이 아닌 회원만 골라 이벤트 본문을 만든다.
	 */
	public static SettlementReconfirmedResponse of(int revisionNo, Long confirmedBy,
			RevisionChangeResponse revisionChange, List<SettlementTransferResponse> transfers) {
		List<NetBalanceChangeResponse> changed = revisionChange.changes().stream()
				.filter(change -> change.delta() != 0)
				.toList();
		return new SettlementReconfirmedResponse(revisionNo, confirmedBy, changed, transfers,
				revisionChange.notice(), revisionChange.shareText());
	}
}
