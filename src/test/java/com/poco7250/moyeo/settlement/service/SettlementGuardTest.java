package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementGuardTest {

	private static final Long MEETING_ID = 1L;
	private static final Long ACTOR_ID = 2L;

	@Mock
	SettlementRepository settlementRepository;

	@Mock
	ApplicationEventPublisher eventPublisher;

	@InjectMocks
	SettlementGuard settlementGuard;

	@Test
	@DisplayName("CONFIRMED 정산은 REOPENED가 되고 재확정 필요 이벤트를 발행한다")
	void markConfirmed() {
		Settlement settlement = givenSettlement(SettlementStatus.CONFIRMED);

		settlementGuard.markAmountChanged(MEETING_ID, ACTOR_ID);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REOPENED);
		verify(eventPublisher).publishEvent(
				new SettlementReopenedEvent(MEETING_ID, new SettlementReopenedResponse(ACTOR_ID)));
	}

	@Test
	@DisplayName("DRAFT 정산은 잠금만 하고 이벤트를 발행하지 않는다")
	void markDraft() {
		Settlement settlement = givenSettlement(SettlementStatus.DRAFT);

		settlementGuard.markAmountChanged(MEETING_ID, ACTOR_ID);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.DRAFT);
		verify(settlementRepository).findByMeetingIdForUpdate(MEETING_ID);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	@DisplayName("이미 REOPENED인 정산은 이벤트를 다시 발행하지 않는다")
	void markReopened() {
		givenSettlement(SettlementStatus.REOPENED);

		settlementGuard.markAmountChanged(MEETING_ID, ACTOR_ID);

		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	@DisplayName("lockOnly는 CONFIRMED 정산의 상태를 바꾸지 않는다")
	void lockOnlyKeepsStatus() {
		Settlement settlement = givenSettlement(SettlementStatus.CONFIRMED);

		settlementGuard.lockOnly(MEETING_ID);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	@DisplayName("정산이 없으면 MEETING_NOT_FOUND다")
	void notFound() {
		given(settlementRepository.findByMeetingIdForUpdate(MEETING_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> settlementGuard.lockForAmountChange(MEETING_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.MEETING_NOT_FOUND);
	}

	private Settlement givenSettlement(SettlementStatus status) {
		Settlement settlement = Settlement.createDraft(null);
		ReflectionTestUtils.setField(settlement, "status", status);
		given(settlementRepository.findByMeetingIdForUpdate(MEETING_ID)).willReturn(Optional.of(settlement));
		return settlement;
	}
}
