package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.config.MeetingLinkProperties;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingPushText;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.push.domain.PushType;
import com.poco7250.moyeo.push.service.PushNotificationEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 모임 웹 푸시(Task 056)의 받는 사람과 문구를 정해 {@link PushNotificationEvent}를 발행한다.
 * 상태를 바꾸는 Service 트랜잭션 안에서 부르고, 전송은 커밋 뒤 PushDispatcher가 한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingPushPublisher {

	private final GroupMemberRepository groupMemberRepository;
	private final AttendanceRepository attendanceRepository;
	private final MeetingLinkProperties meetingLinkProperties;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 새 모임을 만든 사람을 뺀 그룹 멤버 전원에게 알린다.
	 */
	public void meetingCreated(Meeting meeting, Long creatorId) {
		publish(meeting, PushType.MEETING_CREATED, groupMembersExcept(meeting, Set.of(creatorId)),
				"새 모임: " + meeting.getTitle(), MeetingPushText.meetingCreatedBody(meeting.getStartAt()),
				tag(meeting, PushType.MEETING_CREATED));
	}

	/**
	 * 날짜를 확정한 사람을 뺀 그룹 멤버 전원에게 알린다.
	 */
	public void dateConfirmed(Meeting meeting, Long confirmedBy) {
		publish(meeting, PushType.DATE_CONFIRMED, groupMembersExcept(meeting, Set.of(confirmedBy)),
				meeting.getTitle() + " 날짜 확정", MeetingPushText.dateConfirmedBody(meeting.getStartAt()),
				tag(meeting, PushType.DATE_CONFIRMED));
	}

	/**
	 * 확정한 사람과 그 차수 불참 응답자를 뺀 그룹 멤버에게 알린다. 자동 마감이면 confirmedBy가 null이다.
	 */
	public void placeConfirmed(Round round, Long confirmedBy) {
		Meeting meeting = round.getMeeting();
		Set<Long> excluded = new HashSet<>(attendanceRepository.findAbsentMemberIdsByRoundId(round.getId()));
		if (confirmedBy != null) {
			excluded.add(confirmedBy);
		}
		publish(meeting, PushType.PLACE_CONFIRMED, groupMembersExcept(meeting, excluded),
				meeting.getTitle() + " 장소 확정",
				MeetingPushText.placeConfirmedBody(round.getName(), round.confirmedPlace().getLabel()),
				// 차수마다 따로 보이도록 태그에 차수 id를 붙인다
				tag(meeting, PushType.PLACE_CONFIRMED) + "-" + round.getId());
	}

	/**
	 * 미응답자에게 참석 응답을 부탁한다(M-6).
	 */
	void reminder(Meeting meeting, Collection<Long> noResponseIds) {
		publish(meeting, PushType.ATTENDANCE_REMINDER, new HashSet<>(noResponseIds),
				meeting.getTitle() + " 참석 응답 부탁해", MeetingPushText.reminderBody(meeting.getResponseDeadline()),
				tag(meeting, PushType.ATTENDANCE_REMINDER));
	}

	private Set<Long> groupMembersExcept(Meeting meeting, Set<Long> excluded) {
		Set<Long> recipients = new HashSet<>(groupMemberRepository.findMemberIdsByGroupId(meeting.getGroup().getId()));
		recipients.removeAll(excluded);
		return recipients;
	}

	private static String tag(Meeting meeting, PushType type) {
		return "meeting-" + meeting.getId() + "-" + type.name().toLowerCase(Locale.ROOT);
	}

	private void publish(Meeting meeting, PushType type, Set<Long> recipients, String title, String body,
			String tag) {
		if (recipients.isEmpty()) {
			return;
		}
		eventPublisher.publishEvent(new PushNotificationEvent(type, recipients, title, body,
				meetingLinkProperties.urlOf(meeting.getId()), tag));
	}
}
