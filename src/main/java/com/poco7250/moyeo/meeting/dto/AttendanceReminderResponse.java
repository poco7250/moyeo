package com.poco7250.moyeo.meeting.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 참석 미응답자 리마인드(PRD M-6).
 *
 * @param noResponse 한 차수라도 응답하지 않은 멤버(그룹 가입 순, 중복 없음)
 * @param shareText  단톡방에 붙여 넣을 리마인드 문구. 미응답자가 없으면 null
 */
public record AttendanceReminderResponse(Long meetingId, LocalDateTime responseDeadline,
		List<BoardMember> noResponse, String shareText) {
}
