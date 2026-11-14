package com.poco7250.moyeo.meeting.service;

import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ABSENT;
import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.ATTEND;
import static com.poco7250.moyeo.meeting.domain.AttendanceStatus.UNDECIDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceConfirmResponse;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.CheckInResponse;
import com.poco7250.moyeo.meeting.dto.RoundAttendanceItem;
import com.poco7250.moyeo.meeting.dto.RoundBoard;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 당일 체크인·실제 참석자 확정(PRD M-7) 통합 테스트. 재시도 실행기가 활성 트랜잭션을 거부하고 커밋 후 이벤트도 봐야 해서
 * 테스트 트랜잭션을 쓰지 않고 직접 지운다. 앱 Clock이 실제 시각이라 모임 날짜를 오늘(서울) 0시로 둔다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CheckInIntegrationTest {

	private static final LocalDateTime TODAY = LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay();

	@Autowired
	CheckInService checkInService;

	@Autowired
	AttendanceService attendanceService;

	@Autowired
	AttendanceBoardService attendanceBoardService;

	@Autowired
	SettlementConfirmService confirmService;

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

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ChangeLogRepository changeLogRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Member host;
	private Member minsu;
	private Member younghee;
	private Member outsider;
	private final List<GroupMember> memberships = new ArrayList<>();
	private final List<Meeting> meetings = new ArrayList<>();
	private final List<Round> rounds = new ArrayList<>();
	private Meeting meeting;
	private Round meal;
	private Settlement settlement;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = join("철수");
		minsu = join("민수");
		younghee = join("영희");
		outsider = memberRepository.save(Member.ofKakao(System.nanoTime(), "외부인", null, null));
		meeting = saveMeeting(Meeting.create(group, "오늘 모임", TODAY, null, null, host.getId()));
		meal = saveRound(meeting, "밥");
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		expenseRepository.save(Expense.create(meal, host, 90_000L, "밥"));
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		String revisionIds = "(select id from settlement_revision where settlement_id = ?)";
		jdbcTemplate.update("delete from transfer where revision_id in " + revisionIds, settlement.getId());
		jdbcTemplate.update("delete from revision_member_share where revision_id in " + revisionIds,
				settlement.getId());
		jdbcTemplate.update("delete from settlement_revision where settlement_id = ?", settlement.getId());
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
		jdbcTemplate.update("delete from expense where round_id = ?", meal.getId());
		rounds.forEach(r -> jdbcTemplate.update("delete from attendance where round_id = ?", r.getId()));
		settlementRepository.deleteById(settlement.getId());
		// 확정하면 Round.version이 올라가므로 들고 있는 엔티티가 아니라 id로 지운다
		roundRepository.deleteAllById(rounds.stream().map(Round::getId).toList());
		meetingRepository.deleteAll(meetings);
		groupMemberRepository.deleteAll(memberships);
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAll(List.of(host, minsu, younghee, outsider));
	}

	@Test
	@DisplayName("응답 없이 체크인하면 미정 행이 생기고, 같은 체크인 재전송은 알림이 없으며, 취소하면 체크인만 풀린다")
	void checkInWithoutResponse() {
		CheckInResponse response = checkInService.changeCheckIn(meal.getId(), minsu.getId(), minsu.getId(), true);

		assertThat(response).isEqualTo(new CheckInResponse(meal.getId(), minsu.getId(), true));
		assertThat(attendanceOf(minsu).getStatus()).isEqualTo(UNDECIDED);
		assertThat(attendanceOf(minsu).isCheckedIn()).isTrue();
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(CheckInChangedEvent.TYPE, response));

		clearInvocations(messagingTemplate);
		checkInService.changeCheckIn(meal.getId(), minsu.getId(), minsu.getId(), true);
		verify(messagingTemplate, never()).convertAndSend(eq("/topic/meetings/" + meeting.getId()),
				any(RealtimeMessage.class));

		checkInService.changeCheckIn(meal.getId(), minsu.getId(), minsu.getId(), false);
		assertThat(attendanceOf(minsu).isCheckedIn()).isFalse();
		assertThat(attendanceOf(minsu).getStatus()).isEqualTo(UNDECIDED);
		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.DRAFT);
	}

	@Test
	@DisplayName("남의 체크인은 호스트만 바꿀 수 있고, 대상이 그룹 밖이면 404다")
	void proxyCheckIn() {
		assertError(() -> checkInService.changeCheckIn(meal.getId(), minsu.getId(), younghee.getId(), true),
				ErrorCode.NOT_MEETING_HOST);
		assertError(() -> checkInService.changeCheckIn(meal.getId(), host.getId(), outsider.getId(), true),
				ErrorCode.ATTENDANCE_TARGET_NOT_MEMBER);
		assertError(() -> checkInService.changeCheckIn(meal.getId(), outsider.getId(), outsider.getId(), true),
				ErrorCode.NOT_GROUP_MEMBER);

		checkInService.changeCheckIn(meal.getId(), host.getId(), younghee.getId(), true);

		assertThat(attendanceOf(younghee).isCheckedIn()).isTrue();
	}

	@Test
	@DisplayName("모임 당일 전이거나 날짜 투표 중이면 체크인·확정은 409 CHECK_IN_NOT_OPEN")
	void notOpen() {
		Round tomorrow = saveRound(
				saveMeeting(Meeting.create(group, "내일 모임", TODAY.plusDays(1), null, null, host.getId())), "밥");
		Round voting = saveRound(
				saveMeeting(Meeting.createWithDateVote(group, "날짜 미정", null, null, host.getId())), "밥");

		for (Round round : List.of(tomorrow, voting)) {
			assertError(() -> checkInService.changeCheckIn(round.getId(), minsu.getId(), minsu.getId(), true),
					ErrorCode.CHECK_IN_NOT_OPEN);
			assertError(() -> checkInService.confirm(round.getId(), host.getId()), ErrorCode.CHECK_IN_NOT_OPEN);
		}
		assertError(() -> checkInService.confirm(meal.getId(), minsu.getId()), ErrorCode.NOT_MEETING_HOST);
	}

	@Test
	@DisplayName("확정된 정산에서 실제 참석자를 확정하면 체크인 = 참석, 나머지 = 불참이 되고 REOPENED·변경 로그가 남는다")
	void confirmReopensSettlement() {
		respond(host, ATTEND);
		respond(minsu, ATTEND);
		confirmService.confirm(meeting.getId(), host.getId(), false);
		checkInService.changeCheckIn(meal.getId(), host.getId(), host.getId(), true);
		checkInService.changeCheckIn(meal.getId(), younghee.getId(), younghee.getId(), true);
		clearInvocations(messagingTemplate);

		AttendanceConfirmResponse response = checkInService.confirm(meal.getId(), host.getId());

		assertThat(response.attend()).extracting(BoardMember::nickname).containsExactly("철수", "영희");
		assertThat(response.absent()).extracting(BoardMember::nickname).containsExactly("민수");
		assertThat(response.changedCount()).isEqualTo(2);
		assertThat(statusOf(minsu)).isEqualTo(ABSENT);
		assertThat(statusOf(younghee)).isEqualTo(ATTEND);
		assertThat(currentSettlement().getStatus()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(logs()).hasSize(2).allSatisfy(log -> {
			assertThat(log.getActorId()).isEqualTo(host.getId());
			assertThat(log.getEntityType()).isEqualTo(ChangeLogEntityType.ATTENDANCE);
		});
		assertThat(roundRepository.findById(meal.getId()).orElseThrow().getAttendanceConfirmedAt())
				.isEqualTo(response.confirmedAt());
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(RoundAttendanceConfirmedEvent.TYPE, response));
	}

	@Test
	@DisplayName("확정 뒤 늦게 온 멤버가 체크인하고 다시 확정하면 참석으로 바뀌고 현황판에 체크인·확정 시각이 보인다")
	void reconfirmAfterLateCheckIn() {
		checkInService.changeCheckIn(meal.getId(), host.getId(), host.getId(), true);
		checkInService.confirm(meal.getId(), host.getId());
		assertThat(statusOf(minsu)).isEqualTo(ABSENT);

		checkInService.changeCheckIn(meal.getId(), minsu.getId(), minsu.getId(), true);
		AttendanceConfirmResponse again = checkInService.confirm(meal.getId(), host.getId());

		assertThat(again.changedCount()).isEqualTo(1);
		assertThat(statusOf(minsu)).isEqualTo(ATTEND);
		RoundBoard board = attendanceBoardService.getBoard(meeting.getId(), younghee.getId()).rounds().getFirst();
		assertThat(board.checkedInMemberIds()).containsExactly(host.getId(), minsu.getId());
		assertThat(board.attendanceConfirmedAt()).isEqualTo(again.confirmedAt());
		assertThat(board.absent()).extracting(BoardMember::nickname).containsExactly("영희");
	}

	private void assertError(Runnable action, ErrorCode errorCode) {
		assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
	}

	private void respond(Member member, AttendanceStatus status) {
		attendanceService.respondMine(meeting.getId(), member.getId(),
				new AttendanceRespondRequest(null, List.of(new RoundAttendanceItem(meal.getId(), status))));
	}

	private Attendance attendanceOf(Member member) {
		return attendanceRepository.findByRoundIdAndMemberId(meal.getId(), member.getId()).orElseThrow();
	}

	private AttendanceStatus statusOf(Member member) {
		return attendanceOf(member).getStatus();
	}

	private Member join(String nickname) {
		Member member = memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
		memberships.add(groupMemberRepository.save(GroupMember.join(group, member, nickname, GroupRole.MEMBER)));
		return member;
	}

	private Meeting saveMeeting(Meeting meeting) {
		Meeting saved = meetingRepository.save(meeting);
		meetings.add(saved);
		return saved;
	}

	private Round saveRound(Meeting meeting, String name) {
		Round round = roundRepository.save(Round.create(meeting, 1, name));
		rounds.add(round);
		return round;
	}

	private Settlement currentSettlement() {
		return settlementRepository.findById(settlement.getId()).orElseThrow();
	}

	private List<ChangeLog> logs() {
		return changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());
	}
}
