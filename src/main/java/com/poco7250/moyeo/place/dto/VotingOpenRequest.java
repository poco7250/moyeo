package com.poco7250.moyeo.place.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 장소 투표 열기(재투표 포함) 요청. 마감 시각은 필수이고 미래여야 한다.
 */
public record VotingOpenRequest(
		@NotNull(message = "투표 마감 시각을 입력해 주세요.")
		@Future(message = "투표 마감 시각은 지금 이후여야 합니다.")
		LocalDateTime placeVoteDeadline) {
}
