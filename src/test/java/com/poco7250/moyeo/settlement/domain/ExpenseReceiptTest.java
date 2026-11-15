package com.poco7250.moyeo.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpenseReceiptTest {

	@Test
	@DisplayName("교체하면 새 파일 정보로 바뀌고 이전 파일 이름을 돌려준다")
	void replaceReturnsPreviousKey() {
		ExpenseReceipt receipt = ExpenseReceipt.create(null, "old.jpg", ReceiptImageType.JPEG, 100L, 1L,
				LocalDateTime.of(2026, 11, 15, 12, 0));

		String previous = receipt.replace("new.png", ReceiptImageType.PNG, 200L, 2L,
				LocalDateTime.of(2026, 11, 15, 13, 0, 0, 123_456_789));

		assertThat(previous).isEqualTo("old.jpg");
		assertThat(receipt.getStorageKey()).isEqualTo("new.png");
		assertThat(receipt.getImageType()).isEqualTo(ReceiptImageType.PNG);
		assertThat(receipt.getSize()).isEqualTo(200L);
		assertThat(receipt.getUploadedBy()).isEqualTo(2L);
		// DATETIME(6) 정밀도로 자른다
		assertThat(receipt.getUploadedAt()).isEqualTo(LocalDateTime.of(2026, 11, 15, 13, 0, 0, 123_456_000));
	}
}
