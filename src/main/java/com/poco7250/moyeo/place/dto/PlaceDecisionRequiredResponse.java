package com.poco7250.moyeo.place.dto;

import java.util.List;

/**
 * 호스트 선택 요청 본문. tiedCandidateIds가 비어 있으면 고를 후보가 없어 호스트는 재투표만 할 수 있다.
 */
public record PlaceDecisionRequiredResponse(Long roundId, List<Long> tiedCandidateIds) {
}
