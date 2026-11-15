package com.poco7250.moyeo.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.repository.GameResultRepository;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.history.dto.GameHistoryResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.MeetingHistoryPageResponse;
import com.poco7250.moyeo.history.dto.MeetingHistoryResponse;
import com.poco7250.moyeo.history.dto.RoundHistoryResponse;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 히스토리(PRD H-1) 통합 테스트. 지난 모임은 2020년, 다가오는 모임은 2099년으로 둬 실제 시계와 상관없이 구분한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MeetingHistoryIntegrationTest {

	private static final LocalDateTime RESPONDED_AT = LocalDateTime.of(2020, 4, 20, 12, 0);

	private static long nextKakaoId = 960_000L;

	@Autowired
	MeetingHistoryService meetingHistoryService;

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
	PlaceCandidateRepository placeCandidateRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	GameSessionRepository gameSessionRepository;

	@Autowired
	PenaltyRepository penaltyRepository;

	@Autowired
	GameResultRepository gameResultRepository;

	private Group group;
	private Member host;
	private Member minsu;
	private Member younghee;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = joinGroup(group, "호스트");
		minsu = joinGroup(group, "민수");
		younghee = joinGroup(group, "영희");
	}

	@Test
	@DisplayName("지난 모임의 차수별 확정 장소·참석자·지출 합계·정산 상태·최종 회차 벌칙자를 돌려준다")
	void assemblesMeeting() {
		Meeting meeting = savePastMeeting("5월 모임", LocalDateTime.of(2020, 5, 1, 18, 0));
		Round meal = roundRepository.save(Round.create(meeting, 1, "밥"));
		Round bowling = roundRepository.save(Round.create(meeting, 2, "볼링"));
		meal.confirmDirectly(placeCandidateRepository.save(
				PlaceCandidate.propose(meal, Place.of("강남 고깃집", null, null), host)));
		respond(meal, host, AttendanceStatus.ATTEND);
		respond(meal, minsu, AttendanceStatus.ATTEND);
		respond(meal, younghee, AttendanceStatus.ABSENT);
		respond(bowling, younghee, AttendanceStatus.ATTEND);
		respond(bowling, minsu, AttendanceStatus.UNDECIDED);
		expenseRepository.save(Expense.create(meal, host, 50_000L, "고기"));
		expenseRepository.save(Expense.create(bowling, younghee, 37_650L, "볼링"));
		settlementRepository.findByMeetingId(meeting.getId()).orElseThrow().confirm(RESPONDED_AT);
		GameSession rerun = saveRerunGame(bowling);

		MeetingHistoryResponse item = meetingHistoryService.list(group.getId(), minsu.getId(), 0, 20).items().get(0);

		assertThat(item.title()).isEqualTo("5월 모임");
		assertThat(item.attendeeCount()).isEqualTo(3);
		assertThat(item.totalExpenseAmount()).isEqualTo(87_650L);
		assertThat(item.settlementStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(item.settlementRevisionNo()).isEqualTo(1);
		assertThat(item.rounds()).extracting(RoundHistoryResponse::name).containsExactly("밥", "볼링");
		RoundHistoryResponse first = item.rounds().get(0);
		assertThat(first.place().label()).isEqualTo("강남 고깃집");
		assertThat(first.attendees()).extracting(HistoryMemberResponse::nickname).containsExactly("호스트", "민수");
		RoundHistoryResponse second = item.rounds().get(1);
		assertThat(second.place()).isNull();
		assertThat(second.attendees()).extracting(HistoryMemberResponse::nickname).containsExactly("영희");
		assertThat(second.games()).extracting(GameHistoryResponse::gameId).containsExactly(rerun.getId());
		assertThat(second.games().get(0).penalized()).extracting(HistoryMemberResponse::nickname)
				.containsExactly("영희");
		assertThat(second.games().get(0).ordered()).isEmpty();
	}

	@Test
	@DisplayName("순서 정하기 게임은 벌칙자 없이 1번부터 차례로 놓인 참가자를 돌려준다")
	void orderGame() {
		Meeting meeting = savePastMeeting("6월 모임", LocalDateTime.of(2020, 6, 1, 18, 0));
		Round bowling = roundRepository.save(Round.create(meeting, 1, "볼링"));
		GameSession order = gameSessionRepository.save(
				GameSession.create(bowling, GameType.ORDER, host.getId(), PenaltyKind.NONE, "레인 순서", 0));
		order.markAsRoot();
		order.start();
		order.reveal(RESPONDED_AT);
		gameResultRepository.saveAll(List.of(GameResult.ordered(order, minsu, 2),
				GameResult.ordered(order, younghee, 1), GameResult.ordered(order, host, 3)));

		GameHistoryResponse game = meetingHistoryService.list(group.getId(), minsu.getId(), 0, 20)
				.items().get(0).rounds().get(0).games().get(0);

		assertThat(game.type()).isEqualTo(GameType.ORDER);
		assertThat(game.penalized()).isEmpty();
		assertThat(game.ordered()).extracting(HistoryMemberResponse::nickname).containsExactly("영희", "민수", "호스트");
	}

	@Test
	@DisplayName("지출·차수가 없는 모임은 합계 0·DRAFT·빈 차수이고, 다가오는 모임·날짜 투표 중·다른 그룹 모임은 빠진다")
	void filtersMeetings() {
		Meeting past = savePastMeeting("빈 모임", LocalDateTime.of(2020, 4, 1, 18, 0));
		savePastMeeting("다가오는 모임", LocalDateTime.of(2099, 1, 1, 18, 0));
		Meeting voting = meetingRepository.save(Meeting.createWithDateVote(group, "날짜 투표", null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(voting));
		Group other = groupRepository.save(Group.create("다른 그룹"));
		Meeting otherMeeting = meetingRepository.save(
				Meeting.create(other, "남의 모임", LocalDateTime.of(2020, 6, 1, 18, 0), null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(otherMeeting));

		MeetingHistoryPageResponse page = meetingHistoryService.list(group.getId(), host.getId(), 0, 20);

		assertThat(page.items()).extracting(MeetingHistoryResponse::meetingId).containsExactly(past.getId());
		MeetingHistoryResponse item = page.items().get(0);
		assertThat(item.totalExpenseAmount()).isZero();
		assertThat(item.attendeeCount()).isZero();
		assertThat(item.settlementStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(item.settlementRevisionNo()).isNull();
		assertThat(item.rounds()).isEmpty();
	}

	@Test
	@DisplayName("최근 모임 순으로 페이지를 나누고 다음 페이지 유무를 알려준다")
	void pages() {
		Meeting older = savePastMeeting("4월", LocalDateTime.of(2020, 4, 1, 18, 0));
		Meeting newer = savePastMeeting("5월", LocalDateTime.of(2020, 5, 1, 18, 0));

		MeetingHistoryPageResponse first = meetingHistoryService.list(group.getId(), host.getId(), 0, 1);
		MeetingHistoryPageResponse second = meetingHistoryService.list(group.getId(), host.getId(), 1, 1);

		assertThat(first.items()).extracting(MeetingHistoryResponse::meetingId).containsExactly(newer.getId());
		assertThat(first.hasNext()).isTrue();
		assertThat(second.items()).extracting(MeetingHistoryResponse::meetingId).containsExactly(older.getId());
		assertThat(second.hasNext()).isFalse();
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() {
		Member outsider = memberRepository.save(Member.ofKakao(nextKakaoId++, "외부인", null, null));

		assertThatThrownBy(() -> meetingHistoryService.list(group.getId(), outsider.getId(), 0, 20))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}

	/**
	 * 원 회차(벌칙자 민수) → 다시 돌린 회차 공개(벌칙자 영희, 원 벌칙 무효) + 취소된 게임·대기 중 게임을 만든다.
	 */
	private GameSession saveRerunGame(Round round) {
		GameSession original = saveRevealedGame(GameSession.create(round, GameType.LADDER, host.getId(),
				PenaltyKind.MONEY, "꽝이 볼링비", 1), minsu);
		GameSession rerun = saveRevealedGame(GameSession.rerunOf(original, host.getId()), younghee);
		penaltyRepository.findAllByGameSessionIdAndStatusNot(original.getId(), PenaltyStatus.VOIDED).forEach(Penalty::voidOut);
		GameSession canceled = gameSessionRepository.save(
				GameSession.create(round, GameType.DRAW, host.getId(), PenaltyKind.NONE, null, 1));
		canceled.markAsRoot();
		canceled.cancel();
		GameSession waiting = gameSessionRepository.save(
				GameSession.create(round, GameType.DRAW, host.getId(), PenaltyKind.NONE, null, 1));
		waiting.markAsRoot();
		return rerun;
	}

	private GameSession saveRevealedGame(GameSession game, Member penalized) {
		GameSession saved = gameSessionRepository.save(game);
		if (saved.getRootSessionId() == null) {
			saved.markAsRoot();
		}
		saved.start();
		saved.reveal(RESPONDED_AT);
		penaltyRepository.save(Penalty.assign(saved, penalized));
		return saved;
	}

	private Meeting savePastMeeting(String title, LocalDateTime startAt) {
		Meeting meeting = meetingRepository.save(Meeting.create(group, title, startAt, null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(meeting));
		return meeting;
	}

	private void respond(Round round, Member member, AttendanceStatus status) {
		attendanceRepository.save(Attendance.respond(round, member, status, RESPONDED_AT));
	}

	private Member joinGroup(Group target, String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(target, member, nickname, GroupRole.MEMBER));
		return member;
	}
}
