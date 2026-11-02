package com.poco7250.moyeo.meeting.service;

import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ABSENT;
import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ATTEND;
import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceShortcut;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendanceServiceIntegrationTest {

	@Autowired
	AttendanceService attendanceService;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Test
	void 늦참으로_응답한_뒤_1차만으로_다시_응답하면_차수당_1건이_유지되고_값이_바뀐다() {
		Member member = memberRepository.save(Member.ofKakao(2001L, "멤버", null, null));
		Group group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.save(GroupMember.join(group, member, "멤버", GroupRole.MEMBER));
		Meeting meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, member.getId()));
		roundRepository.saveAll(List.of(Round.create(meeting, 1, "밥"), Round.create(meeting, 2, "볼링")));

		attendanceService.respondMine(meeting.getId(), member.getId(),
				new AttendanceRespondRequest(AttendanceShortcut.LATE_FROM_SECOND, null));
		assertThat(statuses(meeting.getId())).containsExactly(ABSENT, ATTEND);

		attendanceService.respondMine(meeting.getId(), member.getId(),
				new AttendanceRespondRequest(AttendanceShortcut.FIRST_ONLY, null));
		assertThat(statuses(meeting.getId())).containsExactly(ATTEND, ABSENT);
	}

	private List<Object> statuses(Long meetingId) {
		return attendanceRepository.findAllByMeetingId(meetingId).stream()
				.sorted(Comparator.comparingInt(a -> a.getRound().getSeq()))
				.map(Attendance::getStatus)
				.map(Object.class::cast)
				.toList();
	}
}
