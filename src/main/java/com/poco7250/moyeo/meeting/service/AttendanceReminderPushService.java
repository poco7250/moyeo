package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.AttendanceReminderPush;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.dto.AttendanceReminderPushResponse;
import com.poco7250.moyeo.meeting.dto.AttendanceReminderResponse;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.ReminderCooldownDetails;
import com.poco7250.moyeo.meeting.repository.AttendanceReminderPushRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미응답자 리마인드 웹 푸시(PRD M-6, Task 056). 그룹 멤버 누구나 보낼 수 있고 모임당 1시간에 한 번이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceReminderPushService {

	private final AttendanceBoardService attendanceBoardService;
	private final MeetingRepository meetingRepository;
	private final AttendanceReminderPushRepository reminderPushRepository;
	private final MemberRepository memberRepository;
	private final MeetingPushPublisher meetingPushPublisher;
	private final Clock clock;

	/**
	 * 한 차수라도 응답하지 않은 멤버에게 리마인드 푸시를 보낸다(커밋 뒤 비동기 전송).
	 * 같은 모임 요청끼리는 모임 행 잠금으로 줄 세워 쿨다운을 한 번만 통과시킨다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 마감 후 ATTENDANCE_REMINDER_CLOSED,
	 *         미응답자 없음 ATTENDANCE_REMINDER_NO_TARGET, 1시간 안 재요청 ATTENDANCE_REMINDER_COOLDOWN(429)
	 */
	@Transactional
	public AttendanceReminderPushResponse push(Long meetingId, Long memberId) {
		AttendanceReminderResponse reminder = attendanceBoardService.getReminder(meetingId, memberId);
		if (reminder.noResponse().isEmpty()) {
			throw new BusinessException(ErrorCode.ATTENDANCE_REMINDER_NO_TARGET);
		}
		Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
		LocalDateTime now = LocalDateTime.now(clock);
		AttendanceReminderPush record = recordSend(meeting, memberRepository.getReferenceById(memberId), now);
		meetingPushPublisher.reminder(meeting, reminder.noResponse().stream().map(BoardMember::memberId).toList());
		log.info("리마인드 푸시: meetingId={}, sentBy={}, recipients={}", meetingId, memberId,
				reminder.noResponse().size());
		return new AttendanceReminderPushResponse(meetingId, reminder.noResponse(), now, record.nextAvailableAt());
	}

	/**
	 * 쿨다운을 검사하고 보낸 기록을 남긴다. 모임 행을 잠근 뒤라 먼저 커밋된 기록을 본다.
	 */
	private AttendanceReminderPush recordSend(Meeting meeting, Member sentBy, LocalDateTime now) {
		return reminderPushRepository.findByMeetingIdForUpdate(meeting.getId())
				.map(record -> {
					if (record.isCoolingDown(now)) {
						throw new BusinessException(ErrorCode.ATTENDANCE_REMINDER_COOLDOWN,
								new ReminderCooldownDetails(record.nextAvailableAt()));
					}
					record.resend(sentBy, now);
					return record;
				})
				.orElseGet(() -> reminderPushRepository.save(AttendanceReminderPush.first(meeting, sentBy, now)));
	}
}
