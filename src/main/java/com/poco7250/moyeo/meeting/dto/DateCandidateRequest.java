package com.poco7250.moyeo.meeting.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 날짜 후보 추가 요청(호스트).
 */
public record DateCandidateRequest(
		@NotNull(message = "날짜 후보 시각을 입력해 주세요.")
		@Future(message = "날짜 후보는 지금 이후 시각이어야 합니다.")
		LocalDateTime startAt) {
}
