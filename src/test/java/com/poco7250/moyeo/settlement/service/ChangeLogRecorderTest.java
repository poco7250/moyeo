package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class ChangeLogRecorderTest {

	private static final Long MEETING_ID = 1L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);

	private final SettlementRepository settlementRepository = mock(SettlementRepository.class);
	private final ChangeLogRepository changeLogRepository = mock(ChangeLogRepository.class);
	private final Meeting meeting = mock(Meeting.class);

	private ChangeLogRecorder recorder;

	@BeforeEach
	void setUp() {
		recorder = new ChangeLogRecorder(settlementRepository, changeLogRepository, JsonMapper.builder().build(),
				TestClocks.fixed(NOW));
	}

	@Test
	@DisplayName("전/후 값을 JSON으로 바꾸고 변경 후 정산 상태와 시각을 함께 남긴다")
	void recordUpdate() {
		givenSettlement(SettlementStatus.REOPENED);

		recorder.record(MEETING_ID, 2L, ChangeLogEntityType.EXPENSE, 3L, ChangeType.UPDATE,
				new AmountSnapshot(1000L), new AmountSnapshot(2000L));

		ChangeLog saved = capturedLog();
		assertThat(saved.getMeeting()).isSameAs(meeting);
		assertThat(saved.getActorId()).isEqualTo(2L);
		assertThat(saved.getChangedAt()).isEqualTo(NOW);
		assertThat(saved.getEntityType()).isEqualTo(ChangeLogEntityType.EXPENSE);
		assertThat(saved.getEntityId()).isEqualTo(3L);
		assertThat(saved.getChangeType()).isEqualTo(ChangeType.UPDATE);
		assertThat(saved.getBeforeJson()).isEqualTo("{\"amount\":1000}");
		assertThat(saved.getAfterJson()).isEqualTo("{\"amount\":2000}");
		assertThat(saved.getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
	}

	@Test
	@DisplayName("없는 전/후 값과 행위자는 null로 남긴다")
	void recordNulls() {
		givenSettlement(SettlementStatus.DRAFT);

		recorder.record(MEETING_ID, null, ChangeLogEntityType.EXPENSE, 3L, ChangeType.CREATE, null,
				new AmountSnapshot(1000L));

		ChangeLog saved = capturedLog();
		assertThat(saved.getActorId()).isNull();
		assertThat(saved.getBeforeJson()).isNull();
		assertThat(saved.getAfterJson()).isEqualTo("{\"amount\":1000}");
	}

	@Test
	@DisplayName("정산이 없으면 MEETING_NOT_FOUND다")
	void notFound() {
		given(settlementRepository.findByMeetingId(MEETING_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> recorder.record(MEETING_ID, 2L, ChangeLogEntityType.EXPENSE, 3L,
				ChangeType.DELETE, new AmountSnapshot(1000L), null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.MEETING_NOT_FOUND);
	}

	private void givenSettlement(SettlementStatus status) {
		Settlement settlement = Settlement.createDraft(meeting);
		ReflectionTestUtils.setField(settlement, "status", status);
		given(settlementRepository.findByMeetingId(MEETING_ID)).willReturn(Optional.of(settlement));
	}

	private ChangeLog capturedLog() {
		ArgumentCaptor<ChangeLog> captor = ArgumentCaptor.forClass(ChangeLog.class);
		verify(changeLogRepository).save(captor.capture());
		return captor.getValue();
	}

	/** 테스트용 화이트리스트 DTO */
	record AmountSnapshot(long amount) implements ChangeLogSnapshot {
	}
}
