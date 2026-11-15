package com.poco7250.moyeo.history.dto;

import com.poco7250.moyeo.meeting.dto.ConfirmedPlaceResponse;
import java.util.List;

/**
 * 히스토리의 차수 한 건.
 *
 * @param place 확정 장소, 확정 안 됐으면 null
 * @param attendees 참석(ATTEND) 응답자, 그룹 가입 순
 */
public record RoundHistoryResponse(Long roundId, int seq, String name, ConfirmedPlaceResponse place,
		List<HistoryMemberResponse> attendees, List<GameHistoryResponse> games) {
}
