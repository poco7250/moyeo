package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.MeetingDateCandidate;
import com.poco7250.moyeo.meeting.domain.RoundTemplate;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * 모임 생성 요청. 길이 제한은 엔티티 컬럼 길이와 맞춘다.
 * 차수는 템플릿(template) 또는 직접 구성(rounds, 1~5개) 중 정확히 하나로 정한다.
 * 날짜는 확정 시각(startAt) 또는 날짜 후보(dateCandidates, 2~10개, 미래 시각) 중 정확히 하나로 정한다(PRD M-5).
 */
public record MeetingCreateRequest(
		@NotBlank(message = "모임 제목을 입력해 주세요.")
		@Size(max = 100, message = "모임 제목은 100자 이하로 입력해 주세요.")
		String title,

		LocalDateTime startAt,

		@Size(max = 1000, message = "메모는 1000자 이하로 입력해 주세요.")
		String memo,

		LocalDateTime responseDeadline,

		RoundTemplate template,

		@Size(max = MAX_ROUNDS, message = "차수는 5개까지 만들 수 있습니다.")
		List<@NotBlank(message = "차수 이름을 입력해 주세요.")
				@Size(max = 30, message = "차수 이름은 30자 이하로 입력해 주세요.") String> rounds,

		@Size(min = MIN_DATE_CANDIDATES, max = MeetingDateCandidate.MAX_PER_MEETING,
				message = "날짜 후보는 2~10개로 골라 주세요.")
		List<@NotNull(message = "날짜 후보 시각을 입력해 주세요.")
				@Future(message = "날짜 후보는 지금 이후 시각이어야 합니다.") LocalDateTime> dateCandidates) {

	public static final int MAX_ROUNDS = 5;
	public static final int MIN_DATE_CANDIDATES = 2;

	/**
	 * 확정 시각과 날짜 후보 중 정확히 하나만 왔는지 검사한다(필드 에러 이름: dateSpecValid).
	 */
	@AssertTrue(message = "모임 날짜·시간 또는 날짜 후보 중 하나를 골라 주세요.")
	public boolean isDateSpecValid() {
		return (startAt != null) != hasDateCandidates();
	}

	/**
	 * 같은 시각을 후보로 두 번 넣지 않았는지 검사한다(필드 에러 이름: dateCandidatesDistinct).
	 */
	@AssertTrue(message = "같은 시각을 날짜 후보로 두 번 넣을 수 없습니다.")
	public boolean isDateCandidatesDistinct() {
		return dateCandidates == null || new HashSet<>(dateCandidates).size() == dateCandidates.size();
	}

	/**
	 * 날짜 후보로 모임을 만드는 요청인지 확인한다.
	 */
	public boolean hasDateCandidates() {
		return dateCandidates != null && !dateCandidates.isEmpty();
	}

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
