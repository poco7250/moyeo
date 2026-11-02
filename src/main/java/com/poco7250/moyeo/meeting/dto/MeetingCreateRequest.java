package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.RoundTemplate;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 생성 요청. 길이 제한은 엔티티 컬럼 길이와 맞춘다.
 * 차수는 템플릿(template) 또는 직접 구성(rounds, 1~5개) 중 정확히 하나로 정한다.
 */
public record MeetingCreateRequest(
		@NotBlank(message = "모임 제목을 입력해 주세요.")
		@Size(max = 100, message = "모임 제목은 100자 이하로 입력해 주세요.")
		String title,

		@NotNull(message = "모임 날짜·시간을 입력해 주세요.")
		LocalDateTime startAt,

		@Size(max = 1000, message = "메모는 1000자 이하로 입력해 주세요.")
		String memo,

		LocalDateTime responseDeadline,

		RoundTemplate template,

		@Size(max = MAX_ROUNDS, message = "차수는 5개까지 만들 수 있습니다.")
		List<@NotBlank(message = "차수 이름을 입력해 주세요.")
				@Size(max = 30, message = "차수 이름은 30자 이하로 입력해 주세요.") String> rounds) {

	public static final int MAX_ROUNDS = 5;

	/**
	 * 템플릿과 직접 구성 중 정확히 하나만 왔는지 검사한다(필드 에러 이름: roundSpecValid).
	 */
	@AssertTrue(message = "차수 템플릿 또는 차수 목록 중 하나를 골라 주세요.")
	public boolean isRoundSpecValid() {
		boolean hasRounds = rounds != null && !rounds.isEmpty();
		return (template != null) != hasRounds;
	}

	/**
	 * 만들 차수 이름을 1차부터 순서대로 돌려준다. 직접 구성은 앞뒤 공백을 지운다.
	 */
	public List<String> resolveRoundNames() {
		if (template != null) {
			return template.roundNames();
		}
		return rounds.stream().map(String::strip).toList();
	}
}
