package com.poco7250.moyeo.meeting.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 차수 실제 참석자 확정 결과(PRD M-7). 체크인한 멤버는 참석, 나머지 그룹 멤버는 불참이 된다. 각 목록은 그룹 가입 순이다.
 *
 * @param changedCount 이번 확정으로 응답이 바뀌거나 새로 생긴 건수
 */
public record AttendanceConfirmResponse(Long meetingId, Long roundId, LocalDateTime confirmedAt,
		List<BoardMember> attend, List<BoardMember> absent, int changedCount) {
}
