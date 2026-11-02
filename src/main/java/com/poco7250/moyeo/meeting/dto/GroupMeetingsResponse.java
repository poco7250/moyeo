package com.poco7250.moyeo.meeting.dto;

import java.util.List;

/**
 * 그룹 홈 모임 목록. 다가오는 모임은 가까운 순, 지난 모임은 최근 순이다.
 */
public record GroupMeetingsResponse(List<MeetingSummaryResponse> upcoming, List<MeetingSummaryResponse> past) {
}
