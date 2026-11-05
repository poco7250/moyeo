package com.poco7250.moyeo.place.dto;

import java.util.List;

/**
 * 차수의 득표 현황. 후보는 등록 순이고 0표 후보도 포함한다.
 */
public record RoundVotesResponse(Long roundId, List<CandidateVotes> candidates) {
}
