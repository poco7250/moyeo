package com.poco7250.moyeo.meeting.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 날짜 후보 하나의 응답 현황. 멤버 목록은 그룹 가입 순서다.
 *
 * @param available 가능
 * @param unavailable 불가
 * @param unanswered 이 후보에 아직 응답하지 않음(응답 뒤에 추가된 후보 포함)
 */
public record DateCandidateVotes(Long candidateId, LocalDateTime startAt, int availableCount,
		List<BoardMember> available, List<BoardMember> unavailable, List<BoardMember> unanswered) {

	/**
	 * 멤버 분류로 현황을 만든다. 가능 인원은 available 크기다.
	 */
	public static DateCandidateVotes of(Long candidateId, LocalDateTime startAt, List<BoardMember> available,
			List<BoardMember> unavailable, List<BoardMember> unanswered) {
		return new DateCandidateVotes(candidateId, startAt, available.size(), available, unavailable, unanswered);
	}
}
