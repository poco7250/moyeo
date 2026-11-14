package com.poco7250.moyeo.meeting.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 내 날짜 응답 설정 요청. 가능한 후보 id 전체를 보내고, 빠진 후보는 불가로 저장한다(빈 목록 = 전부 불가).
 */
public record DateVoteRequest(
		@NotNull(message = "가능한 날짜 후보 목록을 보내 주세요.")
		List<@NotNull(message = "날짜 후보를 골라 주세요.") Long> availableCandidateIds) {
}
