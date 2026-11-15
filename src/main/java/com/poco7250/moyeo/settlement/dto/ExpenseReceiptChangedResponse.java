package com.poco7250.moyeo.settlement.dto;

/**
 * 영수증 사진 변경 알림 본문.
 *
 * @param receipt 바뀐 뒤 사진 정보. 삭제됐으면 null
 */
public record ExpenseReceiptChangedResponse(Long expenseId, ExpenseReceiptResponse receipt) {
}
