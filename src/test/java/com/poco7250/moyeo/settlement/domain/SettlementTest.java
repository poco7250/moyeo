package com.poco7250.moyeo.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementTest {

	@Test
	@DisplayName("DRAFT 정산은 reopen해도 DRAFT를 유지한다")
	void draftStays() {
		Settlement settlement = settlementWith(SettlementStatus.DRAFT);

		assertThat(settlement.reopen()).isFalse();
		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.DRAFT);
	}

	@Test
	@DisplayName("CONFIRMED 정산은 REOPENED가 된다")
	void confirmedReopens() {
		Settlement settlement = settlementWith(SettlementStatus.CONFIRMED);

		assertThat(settlement.reopen()).isTrue();
		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REOPENED);
	}

	@Test
	@DisplayName("REOPENED 정산은 전이 없이 REOPENED를 유지한다")
	void reopenedStays() {
		Settlement settlement = settlementWith(SettlementStatus.REOPENED);

		assertThat(settlement.reopen()).isFalse();
		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REOPENED);
	}

	private Settlement settlementWith(SettlementStatus status) {
		Settlement settlement = Settlement.createDraft(null);
		ReflectionTestUtils.setField(settlement, "status", status);
		return settlement;
	}
}
