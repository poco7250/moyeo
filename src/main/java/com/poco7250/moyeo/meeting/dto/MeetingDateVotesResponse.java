package com.poco7250.moyeo.meeting.dto;

import com.poco7250.moyeo.meeting.domain.MeetingDateStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 날짜 투표 현황. 후보는 이른 시각 순이다.
 *
 * @param startAt 확정된 시작 시각. 투표 중이면 null
 * @param confirmedCandidateId 확정된 후보. 투표 중이거나 후보 없이 날짜를 정한 모임이면 null
 */
public record MeetingDateVotesResponse(Long meetingId, MeetingDateStatus dateStatus, LocalDateTime startAt,
		Long confirmedCandidateId, List<DateCandidateVotes> candidates) {
}
