package com.poco7250.moyeo.meeting.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 차수 하나의 참석 현황. 각 목록은 그룹 가입 순이다.
 *
 * @param checkedInMemberIds    체크인한 멤버 id(그룹 가입 순, PRD M-7)
 * @param attendanceConfirmedAt 호스트가 마지막으로 실제 참석자를 확정한 시각. 확정 전이면 null
 */
public record RoundBoard(
		Long roundId,
		int seq,
		String name,
		List<BoardMember> attend,
		List<BoardMember> absent,
		List<BoardMember> undecided,
		List<BoardMember> noResponse,
		List<Long> checkedInMemberIds,
		LocalDateTime attendanceConfirmedAt) {
}
