package com.poco7250.moyeo.history.dto;

/**
 * 결산 카드의 1인당 부담액 한 줄.
 *
 * @param shareAmount 마지막 확정 리비전의 부담액(원)
 */
public record CardShareResponse(HistoryMemberResponse member, long shareAmount) {
}
