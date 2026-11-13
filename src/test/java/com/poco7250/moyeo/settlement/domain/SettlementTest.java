package com.poco7250.moyeo.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.time.LocalDateTime;
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

	@Test
	@DisplayName("DRAFT 정산을 확정하면 CONFIRMED·리비전 1·최초 확정 시각이 기록된다")
	void confirmFirst() {
		Settlement settlement = settlementWith(SettlementStatus.DRAFT);
		LocalDateTime now = LocalDateTime.of(2026, 12, 20, 22, 0);

		assertThat(settlement.confirm(now)).isEqualTo(1);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(settlement.getCurrentRevisionNo()).isEqualTo(1);
		assertThat(settlement.getFirstConfirmedAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("REOPENED 정산을 재확정하면 CONFIRMED·리비전 +1이고 최초 확정 시각은 그대로다")
	void reconfirm() {
		Settlement settlement = settlementWith(SettlementStatus.DRAFT);
		LocalDateTime first = LocalDateTime.of(2026, 12, 20, 22, 0);
		settlement.confirm(first);
		settlement.reopen();

		assertThat(settlement.confirm(LocalDateTime.of(2026, 12, 21, 10, 0))).isEqualTo(2);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(settlement.getCurrentRevisionNo()).isEqualTo(2);
		assertThat(settlement.getFirstConfirmedAt()).isEqualTo(first);
		settlement.reopen();
		assertThat(settlement.confirm(LocalDateTime.of(2026, 12, 22, 10, 0))).isEqualTo(3);
	}

	@Test
	@DisplayName("CONFIRMED면 확정할 수 없고 상태·리비전 번호도 바뀌지 않는다")
	void confirmRejected() {
		Settlement settlement = settlementWith(SettlementStatus.DRAFT);
		LocalDateTime first = LocalDateTime.of(2026, 12, 20, 22, 0);
		settlement.confirm(first);

		assertThatThrownBy(() -> settlement.confirm(LocalDateTime.of(2026, 12, 21, 10, 0)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SETTLEMENT_ALREADY_CONFIRMED);
		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(settlement.getCurrentRevisionNo()).isEqualTo(1);
		assertThat(settlement.getFirstConfirmedAt()).isEqualTo(first);
	}

	@Test
	@DisplayName("확정 전에는 확정 이력이 없고, 최초 확정 뒤에는 REOPENED가 돼도 확정 이력이 남는다")
	void hasBeenConfirmed() {
		Settlement settlement = settlementWith(SettlementStatus.DRAFT);
		assertThat(settlement.hasBeenConfirmed()).isFalse();

		settlement.confirm(LocalDateTime.of(2026, 12, 20, 22, 0));
		settlement.reopen();

		assertThat(settlement.hasBeenConfirmed()).isTrue();
	}

	private Settlement settlementWith(SettlementStatus status) {
		Settlement settlement = Settlement.createDraft(null);
		ReflectionTestUtils.setField(settlement, "status", status);
		return settlement;
	}
}
