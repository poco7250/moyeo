package com.poco7250.moyeo.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupMemberPaymentTest {

	@Test
	@DisplayName("계좌를 링크로 바꾸면 계좌 3항목이 비워진다")
	void replace() {
		GroupMemberPayment payment = GroupMemberPayment.create(null, "국민", "enc", "철수", null, null);

		payment.replace(null, null, null, "https://toss.me/abc", null);

		assertThat(payment.getBankName()).isNull();
		assertThat(payment.getEncryptedAccountNumber()).isNull();
		assertThat(payment.getAccountHolder()).isNull();
		assertThat(payment.getTossLink()).isEqualTo("https://toss.me/abc");
	}

	@Test
	@DisplayName("계좌 3항목 일부만 있거나 계좌·링크가 모두 없으면 거부한다")
	void rejectsIncomplete() {
		assertThatThrownBy(() -> GroupMemberPayment.create(null, "국민", null, "철수", null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GroupMemberPayment.create(null, null, null, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
