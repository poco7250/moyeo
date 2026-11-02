package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceBoardResponse;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.RoundBoard;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttendanceBoardServiceTest {

	private static final Long GROUP_ID = 1L;
	private static final Long MEETING_ID = 10L;
	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	RoundRepository roundRepository;

	@Mock
	AttendanceRepository attendanceRepository;

	@Mock
	GroupMemberRepository groupMemberRepository;

	private Meeting meeting;
	private Round first;
	private Round second;
	private GroupMember a;
	private GroupMember b;
	private GroupMember c;

	@BeforeEach
	void setUp() {
		Group group = Group.create("대학 동기");
		ReflectionTestUtils.setField(group, "id", GROUP_ID);
		meeting = Meeting.create(group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, DEADLINE, 1L);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		first = round(101L, 1, "밥");
		second = round(102L, 2, "볼링");
		a = groupMember(group, 1L, "가");
		b = groupMember(group, 2L, "나");
		c = groupMember(group, 3L, "다");
	}

	@Test
	@DisplayName("차수별로 참석/불참/미정을 나누고, 응답 없는 그룹 멤버는 미응답이다")
	void board() {
		given(meetingAccessChecker.requireMember(MEETING_ID, 1L)).willReturn(meeting);
		given(roundRepository.findAllByMeetingIdOrderBySeq(MEETING_ID)).willReturn(List.of(first, second));
		given(groupMemberRepository.findAllByGroupIdOrderByIdAsc(GROUP_ID)).willReturn(List.of(a, b, c));
		given(attendanceRepository.findAllByMeetingId(MEETING_ID)).willReturn(List.of(
				Attendance.respond(first, a.getMember(), AttendanceStatus.ATTEND, DEADLINE),
				Attendance.respond(first, b.getMember(), AttendanceStatus.ABSENT, DEADLINE),
				Attendance.respond(second, b.getMember(), AttendanceStatus.ATTEND, DEADLINE),
				Attendance.respond(second, c.getMember(), AttendanceStatus.UNDECIDED, DEADLINE)));

		AttendanceBoardResponse response = new AttendanceBoardService(meetingAccessChecker, roundRepository,
				attendanceRepository, groupMemberRepository, TestClocks.fixed(DEADLINE.plusSeconds(1)))
				.getBoard(MEETING_ID, 1L);

		assertThat(response.closed()).isTrue();
		assertThat(response.responseDeadline()).isEqualTo(DEADLINE);
		RoundBoard meal = response.rounds().get(0);
		assertThat(meal.seq()).isEqualTo(1);
		assertThat(ids(meal.attend())).containsExactly(1L);
		assertThat(ids(meal.absent())).containsExactly(2L);
		assertThat(meal.undecided()).isEmpty();
		assertThat(ids(meal.noResponse())).containsExactly(3L);
		RoundBoard bowling = response.rounds().get(1);
		assertThat(ids(bowling.attend())).containsExactly(2L);
		assertThat(ids(bowling.undecided())).containsExactly(3L);
		assertThat(bowling.noResponse()).extracting(BoardMember::nickname).containsExactly("가");
	}

	private static List<Long> ids(List<BoardMember> members) {
		return members.stream().map(BoardMember::memberId).toList();
	}

	private Round round(Long id, int seq, String name) {
		Round round = Round.create(meeting, seq, name);
		ReflectionTestUtils.setField(round, "id", id);
		return round;
	}

	private static GroupMember groupMember(Group group, Long memberId, String nickname) {
		Member member = Member.ofKakao(memberId * 100, nickname, null, null);
		ReflectionTestUtils.setField(member, "id", memberId);
		return GroupMember.join(group, member, nickname, GroupRole.MEMBER);
	}
}
