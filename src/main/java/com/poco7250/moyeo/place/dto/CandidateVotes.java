package com.poco7250.moyeo.place.dto;

import com.poco7250.moyeo.meeting.dto.BoardMember;
import java.util.List;

/**
 * 후보 하나의 득표 수와 투표자 목록(가입 순). 누가 어디에 투표했는지 공개한다(PRD P-3).
 */
public record CandidateVotes(Long candidateId, int voteCount, List<BoardMember> voters) {

	/**
	 * 투표자 목록으로 만든다. 득표 수는 투표자 수와 같다.
	 */
	public static CandidateVotes of(Long candidateId, List<BoardMember> voters) {
		return new CandidateVotes(candidateId, voters.size(), voters);
	}
}
