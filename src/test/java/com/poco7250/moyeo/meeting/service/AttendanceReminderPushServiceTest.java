package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
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
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AttendanceReminderPushService 단위 테스트. 쿨다운 판단과 발행 대상을 본다.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceReminderPushServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2099, 12, 1, 12, 0);
	private static final List<BoardMember> NO_RESPONSE = List.of(new BoardMember(8L, "민수", null),
			new BoardMember(9L, "영희", null));

	@Mock
	AttendanceBoardService attendanceBoardService;

	@Mock
	MeetingRepository meetingRepository;

	@Mock
	AttendanceReminderPushRepository reminderPushRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	MeetingPushPublisher meetingPushPublisher;

	AttendanceReminderPushService service;
	Meeting meeting;
	Member sender = Member.ofKakao(7L, "호스트", null, null);

	@BeforeEach
	void setUp() {
		service = new AttendanceReminderPushService(attendanceBoardService, meetingRepository, reminderPushRepository,
				memberRepository, meetingPushPublisher, TestClocks.fixed(NOW));
		meeting = Meeting.create(Group.create("대학 동기"), "연말 모임", NOW.plusDays(10), null, null, 7L);
	}

	@Test
	@DisplayName("처음 보내면 기록을 만들고 미응답자에게 발행하며 1시간 뒤를 다음 가능 시각으로 준다")
	void firstPush() {
		givenReminder(NO_RESPONSE);
		given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
		given(memberRepository.getReferenceById(7L)).willReturn(sender);
		given(reminderPushRepository.findByMeetingIdForUpdate(any())).willReturn(Optional.empty());
		given(reminderPushRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		AttendanceReminderPushResponse response = service.push(10L, 7L);

		assertThat(response.sentAt()).isEqualTo(NOW);
		assertThat(response.nextAvailableAt()).isEqualTo(NOW.plusHours(1));
		assertThat(response.noResponse()).isEqualTo(NO_RESPONSE);
		verify(meetingPushPublisher).reminder(meeting, List.of(8L, 9L));
	}

	@Test
	@DisplayName("1시간 안에 다시 보내면 429와 다음 가능 시각, 정확히 1시간 뒤면 다시 보낸다")
	void cooldown() {
		givenReminder(NO_RESPONSE);
		given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
		given(memberRepository.getReferenceById(7L)).willReturn(sender);
		AttendanceReminderPush recent = AttendanceReminderPush.first(meeting, sender, NOW.minusMinutes(59));
		given(reminderPushRepository.findByMeetingIdForUpdate(any())).willReturn(Optional.of(recent));

		assertThatThrownBy(() -> service.push(10L, 7L)).isInstanceOf(BusinessException.class)
				.satisfies(e -> {
					assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ATTENDANCE_REMINDER_COOLDOWN);
					assertThat(((BusinessException) e).getDetails())
							.isEqualTo(new ReminderCooldownDetails(NOW.plusMinutes(1)));
				});
		verify(meetingPushPublisher, never()).reminder(any(), any());

		AttendanceReminderPush hourAgo = AttendanceReminderPush.first(meeting, sender, NOW.minusHours(1));
		given(reminderPushRepository.findByMeetingIdForUpdate(any())).willReturn(Optional.of(hourAgo));
		service.push(10L, 7L);
		assertThat(hourAgo.getLastSentAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("미응답자가 없으면 409 NO_TARGET이고 잠그지도 기록하지도 않는다")
	void noTarget() {
		givenReminder(List.of());

		assertThatThrownBy(() -> service.push(10L, 7L)).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_REMINDER_NO_TARGET);
		verifyNoInteractions(meetingRepository, reminderPushRepository, meetingPushPublisher);
	}

	@Test
	@DisplayName("마감 후·비멤버 거절은 리마인드 조회의 예외를 그대로 낸다")
	void closedPassesThrough() {
		given(attendanceBoardService.getReminder(10L, 7L))
				.willThrow(new BusinessException(ErrorCode.ATTENDANCE_REMINDER_CLOSED));

		assertThatThrownBy(() -> service.push(10L, 7L)).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_REMINDER_CLOSED);
		verifyNoInteractions(meetingRepository, reminderPushRepository);
	}

	private void givenReminder(List<BoardMember> noResponse) {
		given(attendanceBoardService.getReminder(10L, 7L))
				.willReturn(new AttendanceReminderResponse(10L, null, noResponse, noResponse.isEmpty() ? null : "문구"));
	}
}
