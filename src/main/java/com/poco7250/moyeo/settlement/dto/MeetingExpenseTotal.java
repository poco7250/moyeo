package com.poco7250.moyeo.settlement.dto;

/**
 * 모임 하나의 지출 합계(모임 히스토리 조회용 프로젝션).
 *
 * @param totalAmount 지출 금액 합(원)
 */
public record MeetingExpenseTotal(Long meetingId, Long totalAmount) {
}
