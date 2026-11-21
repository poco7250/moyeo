package com.poco7250.moyeo.meeting.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리마인드 푸시 결과(M-6 웹 푸시). 실제 전송은 커밋 뒤 비동기라 기기 수는 담지 않는다.
 *
 * @param noResponse      푸시를 받을 미응답자(그룹 가입 순). 구독·받기 설정이 없는 멤버도 들어 있다
 * @param sentAt          보낸 시각
 * @param nextAvailableAt 다시 보낼 수 있는 시각
 */
public record AttendanceReminderPushResponse(Long meetingId, List<BoardMember> noResponse, LocalDateTime sentAt,
		LocalDateTime nextAvailableAt) {
}
