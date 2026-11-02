package com.poco7250.moyeo.meeting.service;

import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ABSENT;
import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ATTEND;
import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.UNDECIDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceShortcut;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;
import com.poco7250.moyeo.meeting.dto.RoundAttendanceItem;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttendanceWriterTest {

	private static final Long GROUP_ID = 1L;
	private static final Long MEETING_ID = 10L;
	private static final Long HOST_ID = 7L;
	private static final Long MEMBER_ID = 8L;
	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	GroupMemberRepository groupMemberRepository;

	@Mock
	RoundRepository roundRepository;

	@Mock
	AttendanceRepository attendanceRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	ApplicationEventPublisher eventPublisher;

	private Meeting meeting;
	private Round first;
	private Round second;

	@BeforeEach
	void setUp() {
		Group group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
		meeting = Meeting.create(group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, DEADLINE, HOST_ID);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		first = round(101L, 1, "밥");
		second = round(102L, 2, "볼링");
	}

	@Test
	@DisplayName("첫 응답은 늦참을 펼쳐 차수마다 새 행을 저장하고 변경 이벤트를 발행한다")
	void firstResponseWithShortcut() {
		givenMember(MEMBER_ID);
		givenRounds(List.of());
		given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(Member.ofKakao(1L, "멤버", null, null));

		MemberAttendanceResponse response = writer(DEADLINE)
				.respondMine(MEETING_ID, MEMBER_ID, shortcut(AttendanceShortcut.LATE_FROM_SECOND));

		ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
		verify(attendanceRepository, times(2)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(Attendance::getStatus).containsExactly(ABSENT, ATTEND);
		assertThat(response.rounds()).containsExactly(
				new RoundAttendanceItem(101L, ABSENT), new RoundAttendanceItem(102L, ATTEND));
		ArgumentCaptor<AttendanceChangedEvent> event = ArgumentCaptor.forClass(AttendanceChangedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().destination()).isEqualTo("/topic/meetings/10");
		assertThat(event.getValue().data()).isEqualTo(response);
	}

	@Test
	@DisplayName("다시 응답하면 기존 행을 고치고 새 행을 만들지 않는다")
	void reRespondUpdatesExisting() {
		givenMember(MEMBER_ID);
		Attendance existing = Attendance.respond(first, null, ABSENT, DEADLINE.minusDays(1));
		givenRounds(List.of(existing));

		writer(DEADLINE).respondMine(MEETING_ID, MEMBER_ID, rounds(new RoundAttendanceItem(101L, ATTEND)));

		assertThat(existing.getStatus()).isEqualTo(ATTEND);
		assertThat(existing.getRespondedAt()).isEqualTo(DEADLINE);
		verify(attendanceRepository, never()).save(any());
	}

	@Test
	@DisplayName("일부 차수만 바꾸면 응답에는 기존 다른 차수 응답도 함께 담긴다")
	void partialResponseKeepsOthers() {
		givenMember(MEMBER_ID);
		givenRounds(List.of(Attendance.respond(first, null, ATTEND, DEADLINE.minusDays(1))));
		given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(Member.ofKakao(1L, "멤버", null, null));

		MemberAttendanceResponse response = writer(DEADLINE)
				.respondMine(MEETING_ID, MEMBER_ID, rounds(new RoundAttendanceItem(102L, UNDECIDED)));

		assertThat(response.rounds()).containsExactly(
				new RoundAttendanceItem(101L, ATTEND), new RoundAttendanceItem(102L, UNDECIDED));
	}

	@Test
	@DisplayName("마감 1초 뒤 일반 멤버의 응답은 ATTENDANCE_DEADLINE_PASSED로 거부된다")
	void deadlinePassed() {
		givenMember(MEMBER_ID);

		assertThatThrownBy(() -> writer(DEADLINE.plusSeconds(1))
				.respondMine(MEETING_ID, MEMBER_ID, shortcut(AttendanceShortcut.FIRST_ONLY)))
				.extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_DEADLINE_PASSED);
		verifyNoInteractions(attendanceRepository, eventPublisher);
	}

	@Test
	@DisplayName("마감 뒤에도 호스트는 자기 응답을 바꿀 수 있다")
	void hostAfterDeadline() {
		givenMember(HOST_ID);
		givenRoundsFor(HOST_ID, List.of());
		given(memberRepository.getReferenceById(HOST_ID)).willReturn(Member.ofKakao(1L, "호스트", null, null));

		MemberAttendanceResponse response = writer(DEADLINE.plusDays(1))
				.respondMine(MEETING_ID, HOST_ID, shortcut(AttendanceShortcut.FIRST_ONLY));

		assertThat(response.memberId()).isEqualTo(HOST_ID);
	}

	@Test
	@DisplayName("호스트는 마감 뒤에도 다른 멤버 응답을 대신 바꿀 수 있다")
	void hostProxyAfterDeadline() {
		given(meetingAccessChecker.requireHost(MEETING_ID, HOST_ID)).willReturn(meeting);
		given(groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, MEMBER_ID)).willReturn(true);
		givenRounds(List.of());
		given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(Member.ofKakao(1L, "멤버", null, null));

		MemberAttendanceResponse response = writer(DEADLINE.plusDays(1))
				.respondFor(MEETING_ID, HOST_ID, MEMBER_ID, shortcut(AttendanceShortcut.FIRST_ONLY));

		assertThat(response.memberId()).isEqualTo(MEMBER_ID);
		verify(attendanceRepository, times(2)).save(any());
	}

	@Test
	@DisplayName("호스트가 아니면 대리 수정은 NOT_MEETING_HOST다")
	void proxyByNonHost() {
		given(meetingAccessChecker.requireHost(MEETING_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		assertThatThrownBy(() -> writer(DEADLINE)
				.respondFor(MEETING_ID, MEMBER_ID, HOST_ID, shortcut(AttendanceShortcut.FIRST_ONLY)))
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_MEETING_HOST);
		verifyNoInteractions(attendanceRepository);
	}

	@Test
	@DisplayName("대리 수정 대상이 그룹 멤버가 아니면 ATTENDANCE_TARGET_NOT_MEMBER다")
	void proxyTargetNotMember() {
		given(meetingAccessChecker.requireHost(MEETING_ID, HOST_ID)).willReturn(meeting);
		given(groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, 99L)).willReturn(false);

		assertThatThrownBy(() -> writer(DEADLINE)
				.respondFor(MEETING_ID, HOST_ID, 99L, shortcut(AttendanceShortcut.FIRST_ONLY)))
				.extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_TARGET_NOT_MEMBER);
	}

	@Test
	@DisplayName("모임에 없는 차수로 응답하면 ROUND_NOT_FOUND이고 아무것도 저장하지 않는다")
	void unknownRound() {
		givenMember(MEMBER_ID);
		given(roundRepository.findAllByMeetingIdOrderBySeq(MEETING_ID)).willReturn(List.of(first, second));

		assertThatThrownBy(() -> writer(DEADLINE).respondMine(MEETING_ID, MEMBER_ID,
				rounds(new RoundAttendanceItem(101L, ATTEND), new RoundAttendanceItem(999L, ATTEND))))
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_NOT_FOUND);
		verify(attendanceRepository, never()).save(any());
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("차수가 1개인 모임에서 단축 옵션은 ATTENDANCE_SHORTCUT_NOT_APPLICABLE이다")
	void shortcutOnSingleRound() {
		givenMember(MEMBER_ID);
		given(roundRepository.findAllByMeetingIdOrderBySeq(MEETING_ID)).willReturn(List.of(first));

		assertThatThrownBy(() -> writer(DEADLINE)
				.respondMine(MEETING_ID, MEMBER_ID, shortcut(AttendanceShortcut.LATE_FROM_SECOND)))
				.extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_SHORTCUT_NOT_APPLICABLE);
	}

	private AttendanceWriter writer(LocalDateTime now) {
		return new AttendanceWriter(meetingAccessChecker, groupMemberRepository, roundRepository,
				attendanceRepository, memberRepository, eventPublisher, TestClocks.fixed(now));
	}

	private void givenMember(Long memberId) {
		given(meetingAccessChecker.requireMember(MEETING_ID, memberId)).willReturn(meeting);
	}

	private void givenRounds(List<Attendance> existing) {
		givenRoundsFor(MEMBER_ID, existing);
	}

	private void givenRoundsFor(Long memberId, List<Attendance> existing) {
		given(roundRepository.findAllByMeetingIdOrderBySeq(MEETING_ID)).willReturn(List.of(first, second));
		given(attendanceRepository.findAllByMeetingIdAndMemberId(MEETING_ID, memberId)).willReturn(existing);
	}

	private Round round(Long id, int seq, String name) {
		Round round = Round.create(meeting, seq, name);
		ReflectionTestUtils.setField(round, "id", id);
		return round;
	}

	private static AttendanceRespondRequest shortcut(AttendanceShortcut shortcut) {
		return new AttendanceRespondRequest(shortcut, null);
	}

	private static AttendanceRespondRequest rounds(RoundAttendanceItem... items) {
		return new AttendanceRespondRequest(null, List.of(items));
	}
}
