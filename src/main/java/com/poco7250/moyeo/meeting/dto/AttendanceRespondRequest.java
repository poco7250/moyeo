package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.AttendanceShortcut;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import java.util.Objects;

/**
 * 참석 응답 요청. 단축 옵션(shortcut) 또는 차수별 응답(rounds) 중 정확히 하나로 보낸다.
 * 차수별 응답은 일부 차수만 보내도 되고, 보낸 차수만 바뀐다.
 */
public record AttendanceRespondRequest(
		AttendanceShortcut shortcut,

		List<@Valid RoundAttendanceItem> rounds) {

	/**
	 * 단축 옵션과 차수별 응답 중 정확히 하나만 왔는지 검사한다(필드 에러 이름: answerSpecValid).
	 */
	@AssertTrue(message = "단축 옵션 또는 차수별 응답 중 하나를 골라 주세요.")
	public boolean isAnswerSpecValid() {
		boolean hasRounds = rounds != null && !rounds.isEmpty();
		return (shortcut != null) != hasRounds;
	}

	/**
	 * 같은 차수를 두 번 보내지 않았는지 검사한다(필드 에러 이름: roundIdsUnique).
	 */
	@AssertTrue(message = "같은 차수에 응답을 두 번 보낼 수 없습니다.")
	public boolean isRoundIdsUnique() {
		if (rounds == null) {
			return true;
		}
		List<Long> ids = rounds.stream().filter(Objects::nonNull).map(RoundAttendanceItem::roundId)
				.filter(Objects::nonNull).toList();
		return ids.stream().distinct().count() == ids.size();
	}
}
