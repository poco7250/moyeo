package com.poco7250.moyeo.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class AttendanceRepositoryTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MemberRepository memberRepository;

	private Member member;
	private Group group;
	private Round firstRound;
	private Round secondRound;

	@BeforeEach
	void setUp() {
		member = memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, null));
		group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, member.getId()));
		firstRound = roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));
		secondRound = roundRepository.saveAndFlush(Round.create(meeting, 2, "볼링"));
	}

	@Test
	@DisplayName("같은 차수에 같은 회원 응답을 두 번 저장하면 유니크 제약 위반이다")
	void duplicateAttendance() {
		attendanceRepository.saveAndFlush(Attendance.respond(firstRound, member, AttendanceStatus.ATTEND, NOW));

		assertThatThrownBy(() -> attendanceRepository.saveAndFlush(
				Attendance.respond(firstRound, member, AttendanceStatus.ABSENT, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 회원이라도 다른 차수에는 따로 응답할 수 있고 체크인은 기본 false다")
	void sameMemberOtherRound() {
		attendanceRepository.saveAndFlush(Attendance.respond(firstRound, member, AttendanceStatus.ABSENT, NOW));
		Attendance second = attendanceRepository.saveAndFlush(
				Attendance.respond(secondRound, member, AttendanceStatus.ATTEND, NOW));

		assertThat(attendanceRepository.count()).isEqualTo(2);
		assertThat(second.isCheckedIn()).isFalse();
		assertThat(second.getRespondedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("모임 단위 조회는 그 모임 차수의 응답만, 회원별 조회는 그 회원 응답만 돌려준다")
	void findByMeeting() {
		Member other = memberRepository.saveAndFlush(Member.ofKakao(1002L, "친구", null, null));
		Meeting otherMeeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "번개", LocalDateTime.of(2026, 12, 21, 18, 0), null, null, member.getId()));
		Round otherRound = roundRepository.saveAndFlush(Round.create(otherMeeting, 1, "밥"));
		attendanceRepository.saveAndFlush(Attendance.respond(firstRound, member, AttendanceStatus.ATTEND, NOW));
		attendanceRepository.saveAndFlush(Attendance.respond(secondRound, other, AttendanceStatus.ABSENT, NOW));
		attendanceRepository.saveAndFlush(Attendance.respond(otherRound, member, AttendanceStatus.ATTEND, NOW));

		Long meetingId = firstRound.getMeeting().getId();
		assertThat(attendanceRepository.findAllByMeetingId(meetingId))
				.extracting(a -> a.getRound().getId())
				.containsExactlyInAnyOrder(firstRound.getId(), secondRound.getId());
		assertThat(attendanceRepository.findAllByMeetingIdAndMemberId(meetingId, member.getId()))
				.singleElement().extracting(a -> a.getRound().getId()).isEqualTo(firstRound.getId());
	}
}
