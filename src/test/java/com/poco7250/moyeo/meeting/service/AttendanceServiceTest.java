package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.meeting.domain.AttendanceShortcut;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

	private static final AttendanceRespondRequest REQUEST =
			new AttendanceRespondRequest(AttendanceShortcut.FIRST_ONLY, null);
	private static final MemberAttendanceResponse RESPONSE = new MemberAttendanceResponse(8L, List.of());

	@Mock
	AttendanceWriter attendanceWriter;

	@InjectMocks
	AttendanceService attendanceService;

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
