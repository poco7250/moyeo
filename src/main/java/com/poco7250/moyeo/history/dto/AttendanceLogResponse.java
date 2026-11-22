package com.poco7250.moyeo.history.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 출석부 한 줄: 지난 모임 하나와 그 참석자·노쇼 멤버.
 *
 * @param attendees 한 차수라도 참석(ATTEND)한 멤버(그룹 가입 순)
 * @param noShows 한 차수라도 노쇼였던 멤버(그룹 가입 순)
 */
public record AttendanceLogResponse(Long meetingId, String title, LocalDateTime startAt,
		List<HistoryMemberResponse> attendees, List<HistoryMemberResponse> noShows) {
}
