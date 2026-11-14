package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceReminderResponse;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미응답자 리마인드(PRD M-6) 통합 테스트. 읽기 전용이라 테스트 트랜잭션으로 되돌린다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendanceReminderIntegrationTest {

	private static final LocalDateTime START_AT = LocalDateTime.of(2099, 12, 19, 18, 0);
	private static final LocalDateTime NOW = LocalDateTime.of(2099, 12, 1, 12, 0);

	@Autowired
	AttendanceBoardService attendanceBoardService;

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

	private Group group;
	private Member host;
	private Member minsu;
	private Member younghee;
	private Round meal;
	private Round bowling;
	private Meeting meeting;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = join(2001L, "호스트");
		minsu = join(2002L, "민수");
		younghee = join(2003L, "영희");
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", START_AT, null, START_AT.minusDays(1),
				host.getId()));
		meal = roundRepository.save(Round.create(meeting, 1, "밥"));
		bowling = roundRepository.save(Round.create(meeting, 2, "볼링"));
	}

	@Test
	@DisplayName("차수 2개 중 하나라도 응답 안 한 멤버만 가입 순으로 모이고 문구에 닉네임이 들어간다")
	void partialResponses() {
		respond(meal, host, AttendanceStatus.ATTEND);
		respond(bowling, host, AttendanceStatus.ATTEND);
		respond(meal, younghee, AttendanceStatus.ABSENT);

		AttendanceReminderResponse response = attendanceBoardService.getReminder(meeting.getId(), host.getId());

		assertThat(response.noResponse()).extracting(BoardMember::nickname).containsExactly("민수", "영희");
		assertThat(response.shareText()).startsWith("[연말 모임] 12월 19일(토) 18:00")
				.contains("아직 참석 응답 안 한 사람: 민수, 영희")
				.contains("12월 18일(금) 18:00까지 응답 부탁해!")
				.endsWith("/meetings/" + meeting.getId());
	}

	@Test
	@DisplayName("전원이 모든 차수에 응답했으면 빈 목록과 null 문구를 준다")
	void allAnswered() {
		for (Member m : new Member[] {host, minsu, younghee}) {
			respond(meal, m, AttendanceStatus.ATTEND);
			respond(bowling, m, AttendanceStatus.UNDECIDED);
		}

		AttendanceReminderResponse response = attendanceBoardService.getReminder(meeting.getId(), minsu.getId());

		assertThat(response.noResponse()).isEmpty();
		assertThat(response.shareText()).isNull();
	}

	@Test
	@DisplayName("그룹 밖 회원은 403 NOT_GROUP_MEMBER")
	void nonMember() {
		Member outsider = memberRepository.save(Member.ofKakao(2099L, "외부인", null, null));

		assertThatThrownBy(() -> attendanceBoardService.getReminder(meeting.getId(), outsider.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}

	@Test
	@DisplayName("응답 마감이 지난 모임은 409 ATTENDANCE_REMINDER_CLOSED")
	void closed() {
		Meeting past = meetingRepository.save(Meeting.create(group, "지난 모임", START_AT, null,
				LocalDateTime.of(2000, 1, 1, 0, 0), host.getId()));

		assertThatThrownBy(() -> attendanceBoardService.getReminder(past.getId(), host.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_REMINDER_CLOSED);
	}

	private Member join(Long kakaoId, String nickname) {
		Member member = memberRepository.save(Member.ofKakao(kakaoId, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, GroupRole.MEMBER));
		return member;
	}

	private void respond(Round round, Member member, AttendanceStatus status) {
		attendanceRepository.save(Attendance.respond(round, member, status, NOW));
	}
}
