package com.poco7250.moyeo.meeting.dto;

import java.util.List;

/**
 * 차수 하나의 참석 현황. 각 목록은 그룹 가입 순이다.
 */
public record RoundBoard(
		Long roundId,
		int seq,
		String name,
		List<BoardMember> attend,
		List<BoardMember> absent,
		List<BoardMember> undecided,
		List<BoardMember> noResponse) {
}
