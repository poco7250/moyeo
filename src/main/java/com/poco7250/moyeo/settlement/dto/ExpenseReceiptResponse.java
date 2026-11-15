package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.ExpenseReceipt;
import java.time.LocalDateTime;

/**
 * 지출 영수증 사진 정보.
 *
 * @param url 사진을 받는 API 경로. 교체마다 v 값이 바뀌어 브라우저 캐시가 옛 사진을 보여주지 않는다
 */
public record ExpenseReceiptResponse(
		String contentType,
		long size,
		Long uploadedBy,
		LocalDateTime uploadedAt,
		String url) {

	/**
	 * 영수증 엔티티를 응답으로 변환한다. 없으면 null이다.
	 */
	public static ExpenseReceiptResponse from(ExpenseReceipt receipt) {
		if (receipt == null) {
			return null;
		}
		Long expenseId = receipt.getExpense().getId();
		return new ExpenseReceiptResponse(receipt.getImageType().getContentType(), receipt.getSize(),
				receipt.getUploadedBy(), receipt.getUploadedAt(),
				"/api/expenses/" + expenseId + "/receipt?v=" + receipt.getVersion());
	}
}
