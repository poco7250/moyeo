package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.calc.NetBalanceDelta;
import java.util.List;

/**
 * 재확정 응답의 변동액 안내(PRD S-11, E1). 변동액 0인 회원도 목록에 싣는다.
 *
 * @param previousRevisionNo 비교 기준인 직전 확정 리비전 번호
 * @param changes            회원 id 오름차순 변동액
 * @param notice             고정 안내 문구
 */
public record RevisionChangeResponse(int previousRevisionNo, List<NetBalanceChangeResponse> changes, String notice) {

	public static final String NOTICE = "이전 정산대로 이미 송금했다면 변동액만 앱 밖에서 더 보내거나 돌려받으세요. "
			+ "아직 안 보냈다면 새 송금 안내대로 보내면 됩니다.";

	/**
	 * 계산한 변동액으로 안내 응답을 만든다.
	 */
	public static RevisionChangeResponse of(int previousRevisionNo, List<NetBalanceDelta> deltas) {
		return new RevisionChangeResponse(previousRevisionNo,
				deltas.stream().map(NetBalanceChangeResponse::from).toList(), NOTICE);
	}
}
