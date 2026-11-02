package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 차수 하나에 대한 참석 응답(요청·응답 공용).
 */
public record RoundAttendanceItem(
		@NotNull(message = "차수를 골라 주세요.")
		Long roundId,

		@NotNull(message = "참석 여부를 골라 주세요.")
		AttendanceStatus status) {
}
