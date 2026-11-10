package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.concurrency.OptimisticRetryExecutor;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.AttendanceShortcut;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;
import com.poco7250.moyeo.settlement.domain.Settlement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

	private static final AttendanceRespondRequest REQUEST =
			new AttendanceRespondRequest(AttendanceShortcut.FIRST_ONLY, null);
	private static final MemberAttendanceResponse RESPONSE = new MemberAttendanceResponse(8L, List.of());

	@Mock
	AttendanceWriter attendanceWriter;

	@Spy
	OptimisticRetryExecutor retryExecutor = new OptimisticRetryExecutor();

	@InjectMocks
	AttendanceService attendanceService;

	@Test
	@DisplayName("낙관적 락 충돌이 한 번 나면 다시 시도해 성공 응답을 준다")
	void retryOnOptimisticLockFailure() {
		given(attendanceWriter.respondMine(10L, 8L, REQUEST))
				.willThrow(new ObjectOptimisticLockingFailureException(Settlement.class, 1L))
				.willReturn(RESPONSE);

		assertThat(attendanceService.respondMine(10L, 8L, REQUEST)).isEqualTo(RESPONSE);
		verify(attendanceWriter, times(2)).respondMine(10L, 8L, REQUEST);
	}

	@Test
	@DisplayName("낙관적 락 충돌이 3번 이어지면 CONCURRENT_MODIFICATION이다")
	void optimisticLockRetryExhausted() {
		given(attendanceWriter.respondFor(10L, 7L, 8L, REQUEST))
				.willThrow(new ObjectOptimisticLockingFailureException(Settlement.class, 1L));

		assertThatThrownBy(() -> attendanceService.respondFor(10L, 7L, 8L, REQUEST))
				.extracting("errorCode").isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
		verify(attendanceWriter, times(3)).respondFor(10L, 7L, 8L, REQUEST);
	}

	@Test
	@DisplayName("동시 첫 응답으로 유니크 위반이 나면 한 번 더 시도해 성공 응답을 준다")
	void retryOnUniqueViolation() {
		given(attendanceWriter.respondMine(10L, 8L, REQUEST))
				.willThrow(new DataIntegrityViolationException("dup"))
				.willReturn(RESPONSE);

		assertThat(attendanceService.respondMine(10L, 8L, REQUEST)).isEqualTo(RESPONSE);
		verify(attendanceWriter, times(2)).respondMine(10L, 8L, REQUEST);
	}

	@Test
	@DisplayName("대리 수정도 재시도하고, 재시도까지 실패하면 예외를 그대로 던진다")
	void retryFailsPropagates() {
		given(attendanceWriter.respondFor(10L, 7L, 8L, REQUEST))
				.willThrow(new DataIntegrityViolationException("dup"));

		assertThatThrownBy(() -> attendanceService.respondFor(10L, 7L, 8L, REQUEST))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(attendanceWriter, times(2)).respondFor(10L, 7L, 8L, REQUEST);
	}
}
