package com.poco7250.moyeo.settlement.dto;

/**
 * 회원 한 명의 벌칙 부담액 합계(명예의 전당 조회용 프로젝션).
 *
 * @param totalAmount 마지막 확정 리비전 기준 벌칙 부담액 합(원)
 */
public record MemberPenaltyAmount(Long memberId, Long totalAmount) {
}
