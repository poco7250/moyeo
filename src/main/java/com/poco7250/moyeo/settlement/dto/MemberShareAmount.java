package com.poco7250.moyeo.settlement.dto;

/**
 * 회원 한 명의 정산 부담액 합계(연말 결산 조회용 프로젝션).
 *
 * @param totalAmount 모임마다 마지막 확정 리비전 기준 부담액 합(원)
 */
public record MemberShareAmount(Long memberId, Long totalAmount) {
}
