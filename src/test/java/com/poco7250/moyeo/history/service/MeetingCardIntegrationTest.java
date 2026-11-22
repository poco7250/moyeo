package com.poco7250.moyeo.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.poco7250.moyeo.boardgame.domain.BoardGameRank;
import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.boardgame.repository.BoardGameRecordRepository;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.MeetingCardResponse;
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
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
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
 * 모임 결산 카드(PRD H-3) 통합 테스트. 지난 모임은 2019년, 다가오는 모임은 2099년으로 둬 실제 시계와 상관없이 구분한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MeetingCardIntegrationTest {

	private static final LocalDateTime AT = LocalDateTime.of(2019, 4, 20, 12, 0);

	private static long nextKakaoId = 990_000L;

	@Autowired
	MeetingCardService meetingCardService;

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
	SettlementRevisionRepository settlementRevisionRepository;

	@Autowired
	RevisionMemberShareRepository revisionMemberShareRepository;

	@Autowired
	GameSessionRepository gameSessionRepository;

	@Autowired
	PenaltyRepository penaltyRepository;

	@Autowired
	BoardGameRecordRepository boardGameRecordRepository;

	private Group group;
	private Member host;
	private Member minsu;
	private Member younghee;
	private Member jisu;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = joinGroup("호스트");
		minsu = joinGroup("민수");
		younghee = joinGroup("영희");
		jisu = joinGroup("지수");
	}

	@Test
	@DisplayName("지난 모임이면 히스토리와 같은 요약에 마지막 확정 리비전 부담액을 금액 많은 순 → 가입 순으로 붙인다")
	void card() {
		Meeting meeting = saveConfirmedMeeting();

		MeetingCardResponse result = meetingCardService.get(meeting.getId(), jisu.getId());

		assertThat(result.groupName()).isEqualTo("대학 동기");
		assertThat(result.meeting()).isEqualTo(
				meetingHistoryService.list(group.getId(), jisu.getId(), 0, 20).items().getFirst());
		assertThat(result.meeting().totalExpenseAmount()).isEqualTo(80_000L);
		assertThat(result.meeting().settlementStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(result.meeting().settlementRevisionNo()).isEqualTo(2);
		assertThat(result.shares()).extracting(s -> s.member().nickname(), s -> s.shareAmount())
				.containsExactly(tuple("민수", 35_000L), tuple("호스트", 25_000L), tuple("영희", 20_000L),
						tuple("지수", 0L));
	}

	@Test
	@DisplayName("게임은 다시 돌린 회차의 벌칙자만, 보드게임은 꼴등과 함께, 장소는 확정 label로, 노쇼 멤버도 나온다")
	void gamesAndPlaces() {
		Meeting meeting = saveConfirmedMeeting();

		RoundHistoryResponse meal = meetingCardService.get(meeting.getId(), host.getId()).meeting().rounds()
				.getFirst();

		assertThat(meal.place().label()).isEqualTo("강남 고깃집");
		assertThat(meal.attendees()).extracting(HistoryMemberResponse::nickname)
				.containsExactly("호스트", "민수", "영희");
		assertThat(meal.noShows()).extracting(HistoryMemberResponse::nickname).containsExactly("지수");
		assertThat(meal.games()).hasSize(1);
		assertThat(meal.games().getFirst().penalized()).extracting(HistoryMemberResponse::nickname)
				.containsExactly("영희");
		assertThat(meal.boardGames().getFirst().loser().nickname()).isEqualTo("민수");
	}

	@Test
	@DisplayName("한 번도 확정 안 된 모임이면 부담액은 빈 목록이고 요약은 그대로 나온다")
	void unconfirmed() {
		Meeting meeting = savePastMeeting();
		Round round = roundRepository.save(Round.create(meeting, 1, "밥"));
		expenseRepository.save(Expense.create(round, host, 30_000L, "밥"));

		MeetingCardResponse result = meetingCardService.get(meeting.getId(), host.getId());

		assertThat(result.shares()).isEmpty();
		assertThat(result.meeting().totalExpenseAmount()).isEqualTo(30_000L);
		assertThat(result.meeting().settlementStatus()).isEqualTo(SettlementStatus.DRAFT);
	}

	@Test
	@DisplayName("다가오는 모임·날짜 투표 중 모임은 409 MEETING_NOT_PAST")
	void rejectsNotPast() {
		Meeting upcoming = meetingRepository.save(
				Meeting.create(group, "다가오는 모임", LocalDateTime.of(2099, 1, 1, 18, 0), null, null, host.getId()));
		Meeting voting = meetingRepository.save(Meeting.createWithDateVote(group, "날짜 투표", null, null, host.getId()));

		for (Meeting meeting : List.of(upcoming, voting)) {
			assertThatThrownBy(() -> meetingCardService.get(meeting.getId(), host.getId()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.MEETING_NOT_PAST);
		}
	}

	@Test
	@DisplayName("그룹 멤버가 아니거나 없는 모임이면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() {
		Meeting meeting = savePastMeeting();
		Member outsider = memberRepository.save(Member.ofKakao(nextKakaoId++, "외부인", null, null));

		assertThatThrownBy(() -> meetingCardService.get(meeting.getId(), outsider.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		assertThatThrownBy(() -> meetingCardService.get(Long.MAX_VALUE, host.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}

	/**
	 * 2019년 3월 모임: 1차 "강남 고깃집"(호스트·민수·영희 참석, 지수 노쇼), 지출 50,000·30,000원, 리비전 1 → 재확정 리비전 2
	 * (호스트 25,000·민수 35,000·영희 20,000·지수 0). 금액형 사다리 민수 꽝 → 다시 돌린 회차 영희 꽝, 보드게임 꼴등 민수.
	 */
	private Meeting saveConfirmedMeeting() {
		Meeting meeting = savePastMeeting();
		Round meal = roundRepository.save(Round.create(meeting, 1, "밥"));
		meal.confirmDirectly(placeCandidateRepository.save(
				PlaceCandidate.propose(meal, Place.of("강남 고깃집", null, null), host)));
		for (Member member : List.of(host, minsu, younghee)) {
			attendanceRepository.save(Attendance.respond(meal, member, AttendanceStatus.ATTEND, AT));
		}
		// 지수는 참석한다고 했지만 체크인이 없어 실제 참석자 확정 때 노쇼가 됐다
		Attendance jisuNoShow = Attendance.respond(meal, jisu, AttendanceStatus.ATTEND, AT);
		jisuNoShow.recordNoShowIfMissed();
		jisuNoShow.changeStatus(AttendanceStatus.ABSENT, AT);
		attendanceRepository.save(jisuNoShow);
		expenseRepository.save(Expense.create(meal, host, 50_000L, "고기"));
		expenseRepository.save(Expense.create(meal, minsu, 30_000L, "술"));
		Settlement settlement = settlementRepository.findByMeetingId(meeting.getId()).orElseThrow();
		saveRevision(settlement, 30_000L, 30_000L, 20_000L);
		settlement.reopen();
		saveRevision(settlement, 25_000L, 35_000L, 20_000L);
		saveRerunLadder(meal);
		boardGameRecordRepository.save(BoardGameRecord.create(meal, "스플렌더", host.getId(),
				List.of(BoardGameRank.of(host.getId(), 1), BoardGameRank.of(minsu.getId(), 2)), minsu.getId()));
		return meeting;
	}

	private void saveRerunLadder(Round round) {
		GameSession original = saveRevealedGame(GameSession.create(round, GameType.LADDER, host.getId(),
				PenaltyKind.MONEY, null, 1));
		penaltyRepository.save(Penalty.assign(original, minsu)).voidOut();
		GameSession rerun = saveRevealedGame(GameSession.rerunOf(original, host.getId()));
		penaltyRepository.save(Penalty.assign(rerun, younghee));
	}

	private void saveRevision(Settlement settlement, long hostShare, long minsuShare, long youngheeShare) {
		int revisionNo = settlement.confirm(AT);
		SettlementRevision revision = settlementRevisionRepository.save(
				SettlementRevision.create(settlement, revisionNo, host, AT, false));
		revisionMemberShareRepository.save(RevisionMemberShare.create(revision, host, hostShare, 0L));
		revisionMemberShareRepository.save(RevisionMemberShare.create(revision, minsu, minsuShare, 0L));
		revisionMemberShareRepository.save(RevisionMemberShare.create(revision, younghee, youngheeShare, 0L));
		revisionMemberShareRepository.save(RevisionMemberShare.create(revision, jisu, 0L, 0L));
	}

	private GameSession saveRevealedGame(GameSession game) {
		GameSession saved = gameSessionRepository.save(game);
		if (saved.getRootSessionId() == null) {
			saved.markAsRoot();
		}
		saved.start();
		saved.reveal(AT);
		return saved;
	}

	private Meeting savePastMeeting() {
		Meeting meeting = meetingRepository.save(
				Meeting.create(group, "3월 모임", LocalDateTime.of(2019, 3, 1, 18, 0), null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(meeting));
		return meeting;
	}

	private Member joinGroup(String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(group, member, nickname, GroupRole.MEMBER));
		return member;
	}
}
