package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.calc.MemberSettlement;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;

/**
 * 회원별 부담액·순잔액 응답. 순잔액은 받을 돈이 +, 낼 돈이 −다.
 */
public record SettlementMemberResponse(Long memberId, long shareAmount, long netBalance) {

	/**
	 * 현재 계산값을 응답으로 변환한다.
	 */
	public static SettlementMemberResponse from(MemberSettlement member) {
		return new SettlementMemberResponse(member.memberId(), member.shareAmount(), member.netBalance());
	}

	/**
	 * 저장된 리비전 스냅샷을 응답으로 변환한다.
	 */
	public static SettlementMemberResponse from(RevisionMemberShare share) {
		return new SettlementMemberResponse(share.getMember().getId(), share.getShareAmount(), share.getNetBalance());
	}
}
