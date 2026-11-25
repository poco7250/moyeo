package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.dto.GameParticipantResponse;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.service.GameLobbyService;
import com.poco7250.moyeo.game.service.GameRerunService;
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
import com.poco7250.moyeo.meeting.domain.MeetingGuest;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.MeetingGuestResponse;
import com.poco7250.moyeo.meeting.dto.MeetingGuestsResponse;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.settlement.service.SettlementQueryService;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
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
 * 모임 게스트(PRD G-6) 통합 테스트. 게스트 추가·삭제, 게임 참가, 게스트 벌칙의 정산 제외를 본다.
 * 커밋 후 실시간 전송과 재시도 실행기를 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MeetingGuestIntegrationTest {

	private static long nextKakaoId = 950_000L;

	@Autowired
	MeetingGuestService meetingGuestService;

	@Autowired
	MeetingService meetingService;

	@Autowired
	GameLobbyService gameLobbyService;

	@Autowired
	GameRerunService gameRerunService;

	@Autowired
	SettlementQueryService settlementQueryService;

	@Autowired
	SettlementConfirmService settlementConfirmService;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ChangeLogRepository changeLogRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Member owner;
	private Member host;
	private Member minsu;
	private Member younghee;
	private Member outsider;
	private final List<Meeting> meetings = new ArrayList<>();
	private Meeting meeting;
	private Round round;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		owner = join("방장", GroupRole.OWNER);
		host = join("호스트", GroupRole.MEMBER);
		minsu = join("민수", GroupRole.MEMBER);
		younghee = join("영희", GroupRole.MEMBER);
		outsider = memberRepository.save(Member.ofKakao(nextKakaoId++, "외부인", null, null));
		meeting = saveMeeting("연말 모임");
		round = roundRepository.save(Round.create(meeting, 1, "볼링"));
		for (Member member : List.of(host, minsu, younghee)) {
			attendanceRepository.save(Attendance.respond(round, member, AttendanceStatus.ATTEND,
					LocalDateTime.of(2026, 12, 1, 12, 0)));
		}
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		meetings.forEach(this::deleteMeetingData);
		meetingRepository.deleteAllById(meetings.stream().map(Meeting::getId).toList());
		jdbcTemplate.update("delete from group_member where group_id = ?", group.getId());
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(List.of(owner.getId(), host.getId(), minsu.getId(), younghee.getId(),
				outsider.getId()));
	}

	// 모임 하나의 게임·정산·참석·게스트 데이터를 FK 역순으로 지운다
	private void deleteMeetingData(Meeting target) {
		Long id = target.getId();
		String rounds = "(select id from meeting_round where meeting_id = " + id + ")";
		String games = "(select id from game_session where meeting_id = " + id + ")";
		String revisions = "(select id from settlement_revision where settlement_id in "
				+ "(select id from settlement where meeting_id = " + id + "))";
		List<Long> guestIds = jdbcTemplate.queryForList(
				"select member_id from meeting_guest where meeting_id = ?", Long.class, id);
		jdbcTemplate.update("delete from change_log where meeting_id = ?", id);
		jdbcTemplate.update("delete from penalty_charge where revision_id in " + revisions);
		jdbcTemplate.update("delete from transfer where revision_id in " + revisions);
		jdbcTemplate.update("delete from revision_member_share where revision_id in " + revisions);
		jdbcTemplate.update("delete from settlement_revision where settlement_id in "
				+ "(select id from settlement where meeting_id = ?)", id);
		jdbcTemplate.update("delete from penalty where game_session_id in " + games);
		jdbcTemplate.update("delete from game_result where game_session_id in " + games);
		jdbcTemplate.update("delete from game_participant where game_session_id in " + games);
		// 다시 돌린 회차가 원 회차를 가리키므로 참조를 먼저 비운다
		jdbcTemplate.update(
				"update game_session set rerun_of_id = null, canceled_rerun_of_id = null where meeting_id = ?", id);
		jdbcTemplate.update("delete from game_session where meeting_id = ?", id);
		jdbcTemplate.update("delete from settlement where meeting_id = ?", id);
		jdbcTemplate.update("delete from attendance where round_id in " + rounds);
		jdbcTemplate.update("delete from meeting_round where meeting_id = ?", id);
		jdbcTemplate.update("delete from meeting_guest where meeting_id = ?", id);
		guestIds.forEach(guestId -> jdbcTemplate.update("delete from member where id = ? and guest = true", guestId));
	}

	@Test
	@DisplayName("호스트와 방장은 게스트를 추가할 수 있고 모임 상세·실시간에 보인다. 일반 멤버·비멤버는 403이다")
	void addGuests() {
		MeetingGuestResponse added = meetingGuestService.add(meeting.getId(), host.getId(), "  민수 친구 ");
		meetingGuestService.add(meeting.getId(), owner.getId(), "영희 동생");

		assertThat(added.name()).isEqualTo("민수 친구");
		assertThat(meetingService.getDetail(meeting.getId(), minsu.getId()).guests())
				.extracting(MeetingGuestResponse::name).containsExactly("민수 친구", "영희 동생");
		verify(messagingTemplate).convertAndSend("/topic/meetings/" + meeting.getId(),
				(Object) new RealtimeMessage(MeetingGuestsChangedEvent.TYPE,
						new MeetingGuestsResponse(meeting.getId(), List.of(added))));
		assertError(() -> meetingGuestService.add(meeting.getId(), minsu.getId(), "친구"), ErrorCode.NOT_MEETING_HOST);
		assertError(() -> meetingGuestService.add(meeting.getId(), outsider.getId(), "친구"),
				ErrorCode.NOT_GROUP_MEMBER);
	}

	@Test
	@DisplayName("같은 모임에 같은 이름은 409, 21번째는 409다. 다른 모임엔 같은 이름을 다른 게스트로 만들 수 있다")
	void duplicateAndLimit() {
		MeetingGuestResponse first = meetingGuestService.add(meeting.getId(), host.getId(), "게스트1");
		assertError(() -> meetingGuestService.add(meeting.getId(), host.getId(), " 게스트1 "),
				ErrorCode.MEETING_GUEST_DUPLICATED);
		for (int i = 2; i <= MeetingGuest.MAX_PER_MEETING; i++) {
			meetingGuestService.add(meeting.getId(), host.getId(), "게스트" + i);
		}
		assertError(() -> meetingGuestService.add(meeting.getId(), host.getId(), "게스트21"),
				ErrorCode.MEETING_GUEST_LIMIT);

		Meeting next = saveMeeting("다음 모임");
		MeetingGuestResponse again = meetingGuestService.add(next.getId(), host.getId(), "게스트1");

		assertThat(again.guestId()).isNotEqualTo(first.guestId());
	}

	@Test
	@DisplayName("게스트는 그 모임 게임에 참가자로 들어가고 다시 돌려도 남는다. 다른 모임 게스트는 400, 참가한 게스트는 못 지운다")
	void guestsJoinGames() {
		MeetingGuestResponse guest = meetingGuestService.add(meeting.getId(), host.getId(), "민수 친구");
		MeetingGuestResponse unused = meetingGuestService.add(meeting.getId(), host.getId(), "안 온 친구");
		Meeting other = saveMeeting("다른 모임");
		MeetingGuestResponse otherGuest = meetingGuestService.add(other.getId(), host.getId(), "남의 게스트");

		GameStateResponse game = createGame(PenaltyKind.ACTION, 1, List.of(minsu.getId(), guest.guestId()));

		assertThat(game.participants()).extracting(GameParticipantResponse::nickname, GameParticipantResponse::guest)
				.containsExactly(tuple("민수", false),
						tuple("민수 친구", true));
		GameStateResponse rerun = gameRerunService.request(gameLobbyService.start(game.gameId(), host.getId()).gameId(),
				host.getId());
		assertThat(rerun.participants()).extracting(GameParticipantResponse::memberId)
				.containsExactly(minsu.getId(), guest.guestId());
		assertError(() -> createGame(PenaltyKind.ACTION, 1, List.of(minsu.getId(), otherGuest.guestId())),
				ErrorCode.GAME_PARTICIPANT_NOT_ATTENDING);
		assertError(() -> meetingGuestService.remove(meeting.getId(), host.getId(), guest.guestId()),
				ErrorCode.MEETING_GUEST_IN_USE);
		assertError(() -> meetingGuestService.remove(meeting.getId(), host.getId(), otherGuest.guestId()),
				ErrorCode.MEETING_GUEST_NOT_FOUND);

		meetingGuestService.remove(meeting.getId(), host.getId(), unused.guestId());

		assertThat(meetingService.getDetail(meeting.getId(), host.getId()).guests())
				.extracting(MeetingGuestResponse::guestId).containsExactly(guest.guestId());
		assertThat(memberRepository.findById(unused.guestId())).isEmpty();
	}

	@Test
	@DisplayName("금액형에서 게스트만 당첨되면 확정된 정산은 그대로고, 정산 탭에는 '앱 밖에서 내기'(게스트·부담액 없음)로만 보인다")
	void guestOnlyMoneyPenaltyIsOutsideSettlement() {
		List<Long> guests = List.of(
				meetingGuestService.add(meeting.getId(), host.getId(), "게스트A").guestId(),
				meetingGuestService.add(meeting.getId(), host.getId(), "게스트B").guestId(),
				meetingGuestService.add(meeting.getId(), host.getId(), "게스트C").guestId());
		settlementConfirmService.confirm(meeting.getId(), host.getId(), false);

		GameStateResponse started = gameLobbyService.start(createGame(PenaltyKind.MONEY, 2, guests).gameId(),
				host.getId());

		assertThat(started.result().penaltyMemberIds()).hasSize(2).isSubsetOf(guests);
		assertThat(settlementOf(meeting).getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).isEmpty();
		assertThat(settlementOf(meeting).getCurrentRevisionNo()).isEqualTo(1);
		assertThat(settlementQueryService.get(meeting.getId(), minsu.getId()).penalties()).hasSize(2)
				.allSatisfy(item -> {
					assertThat(item.guest()).isTrue();
					assertThat(item.chargeAmount()).isNull();
				});
	}

	@Test
	@DisplayName("금액형에서 멤버와 게스트가 같이 당첨되면 멤버 벌칙만 정산에 반영되고, 게스트는 나눠 받는 사람에서도 빠진다")
	void memberPenaltyStaysInSettlement() {
		Long guestId = meetingGuestService.add(meeting.getId(), host.getId(), "게스트A").guestId();

		GameStateResponse started = gameLobbyService.start(
				createGame(PenaltyKind.MONEY, 2, List.of(minsu.getId(), younghee.getId(), guestId)).gameId(),
				host.getId());

		List<Long> penalizedMembers = started.result().penaltyMemberIds().stream()
				.filter(id -> !id.equals(guestId)).toList();
		assertThat(penalizedMembers).isNotEmpty();
		List<SettlementPenaltyResponse> items = settlementQueryService.get(meeting.getId(), minsu.getId()).penalties();
		assertThat(items).filteredOn(item -> !item.guest())
				.extracting(SettlementPenaltyResponse::memberId).containsExactlyInAnyOrderElementsOf(penalizedMembers);
		assertThat(items).filteredOn(item -> !item.guest())
				.allSatisfy(item -> assertThat(item.chargeAmount()).isNotNull());
		assertThat(items).allSatisfy(item -> assertThat(item.creditedMemberIds()).doesNotContain(guestId));
		assertThat(settlementConfirmService.confirm(meeting.getId(), host.getId(), false).status())
				.isEqualTo(SettlementStatus.CONFIRMED);
	}

	private GameStateResponse createGame(PenaltyKind penaltyKind, int penaltyCount, List<Long> participantIds) {
		return gameLobbyService.create(round.getMeeting().getId(), host.getId(),
				new GameCreateRequest(GameType.LADDER, penaltyKind, penaltyKind == PenaltyKind.MONEY ? 10_000L : null, null, participantIds, penaltyCount));
	}

	private void assertError(Runnable action, ErrorCode errorCode) {
		assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
	}

	private Settlement settlementOf(Meeting target) {
		return settlementRepository.findByMeetingId(target.getId()).orElseThrow();
	}

	private Meeting saveMeeting(String title) {
		Meeting saved = meetingRepository.save(Meeting.create(group, title, LocalDateTime.of(2099, 12, 20, 18, 0),
				null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(saved));
		meetings.add(saved);
		return saved;
	}

	private Member join(String nickname, GroupRole role) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, role));
		return member;
	}
}
