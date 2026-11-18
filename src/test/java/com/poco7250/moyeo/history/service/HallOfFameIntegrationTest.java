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
import com.poco7250.moyeo.history.dto.AttendanceLogResponse;
import com.poco7250.moyeo.history.dto.HallOfFameResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
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
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.PenaltyChargeRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 명예의 전당(PRD H-2) 통합 테스트. 지난 모임은 2019·2020년, 다가오는 모임은 2099년으로 둬 실제 시계와 상관없이 구분한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HallOfFameIntegrationTest {

	private static final LocalDateTime AT = LocalDateTime.of(2019, 4, 20, 12, 0);

	private static long nextKakaoId = 970_000L;

	@Autowired
	HallOfFameService hallOfFameService;

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
	SettlementRevisionRepository settlementRevisionRepository;

	@Autowired
	PenaltyChargeRepository penaltyChargeRepository;

	@Autowired
	GameSessionRepository gameSessionRepository;

	@Autowired
	PenaltyRepository penaltyRepository;

	@Autowired
	PenaltyRuleRepository penaltyRuleRepository;

	@Autowired
	BoardGameRecordRepository boardGameRecordRepository;

	private Group group;
	private Member host;
	private Member minsu;
	private Member younghee;
	private Member jisu;
	private Meeting may2019;
	private Meeting june2020;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = joinGroup(group, "호스트");
		minsu = joinGroup(group, "민수");
		younghee = joinGroup(group, "영희");
		jisu = joinGroup(group, "지수");
		may2019 = saveMay2019();
		june2020 = saveJune2020();
		saveExcludedMeetings();
	}

	@Test
	@DisplayName("전체 기간: 무효 벌칙은 빼고 면제·행동형은 세며, 금액은 마지막 확정 리비전 기준이다. 멤버 전원이 경쟁 순위로 나온다")
	void allTime() {
		HallOfFameResponse result = hallOfFameService.get(group.getId(), jisu.getId(), null);

		assertThat(result.year()).isNull();
		assertThat(result.penaltyRanking())
				.extracting(r -> r.member().nickname(), r -> r.rank(), r -> r.penaltyCount(), r -> r.penaltyAmount())
				.containsExactly(tuple("영희", 1, 2L, 0L), tuple("민수", 2, 1L, 12_000L), tuple("호스트", 3, 0L, 0L),
						tuple("지수", 3, 0L, 0L));
		assertThat(result.boardGameLoserRanking())
				.extracting(r -> r.member().nickname(), r -> r.rank(), r -> r.loserCount(), r -> r.playCount())
				.containsExactly(tuple("민수", 1, 1L, 1L), tuple("영희", 1, 1L, 2L), tuple("호스트", 3, 0L, 1L),
						tuple("지수", 3, 0L, 0L));
		assertThat(result.attendanceLog()).extracting(AttendanceLogResponse::meetingId)
				.containsExactly(june2020.getId(), may2019.getId());
		assertThat(result.attendanceLog().get(0).attendees()).extracting(HistoryMemberResponse::nickname)
				.containsExactly("영희", "지수");
		assertThat(result.attendanceLog().get(1).attendees()).extracting(HistoryMemberResponse::nickname)
				.containsExactly("호스트", "민수");
	}

	@Test
	@DisplayName("연도를 주면 그해에 시작한 모임만 센다. 횟수가 같으면 금액이 많은 순이고 순위는 같다")
	void byYear() {
		HallOfFameResponse result = hallOfFameService.get(group.getId(), host.getId(), 2019);

		assertThat(result.year()).isEqualTo(2019);
		assertThat(result.penaltyRanking())
				.extracting(r -> r.member().nickname(), r -> r.rank(), r -> r.penaltyCount(), r -> r.penaltyAmount())
				.containsExactly(tuple("민수", 1, 1L, 12_000L), tuple("영희", 1, 1L, 0L), tuple("호스트", 3, 0L, 0L),
						tuple("지수", 3, 0L, 0L));
		assertThat(result.attendanceLog()).extracting(AttendanceLogResponse::meetingId)
				.containsExactly(may2019.getId());
		assertThat(result.boardGameLoserRanking()).extracting(r -> r.loserCount()).containsExactly(1L, 0L, 0L, 0L);
	}

	@Test
	@DisplayName("모임이 없는 연도면 출석부는 비고 순위는 멤버 전원이 0회 공동 1위다")
	void emptyYear() {
		HallOfFameResponse result = hallOfFameService.get(group.getId(), host.getId(), 2018);

		assertThat(result.attendanceLog()).isEmpty();
		assertThat(result.penaltyRanking()).extracting(r -> r.member().nickname(), r -> r.rank())
				.containsExactly(tuple("호스트", 1), tuple("민수", 1), tuple("영희", 1), tuple("지수", 1));
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() {
		Member outsider = memberRepository.save(Member.ofKakao(nextKakaoId++, "외부인", null, null));

		assertThatThrownBy(() -> hallOfFameService.get(group.getId(), outsider.getId(), null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}

	/**
	 * 2019년 5월: 호스트·민수 참석. 금액형 사다리 민수 꽝(리비전 1 10,000원 → 재확정 리비전 2 12,000원),
	 * 행동형 꽝뽑기는 호스트 꽝 → 다시 돌린 회차 영희 꽝(호스트 벌칙 무효). 보드게임 꼴등 민수, 꼴등 지정 전 기록 1건.
	 */
	private Meeting saveMay2019() {
		Meeting meeting = savePastMeeting("5월 모임", LocalDateTime.of(2019, 5, 1, 18, 0));
		Round round = roundRepository.save(Round.create(meeting, 1, "밥"));
		respond(round, host, AttendanceStatus.ATTEND);
		respond(round, minsu, AttendanceStatus.ATTEND);
		respond(round, younghee, AttendanceStatus.ABSENT);
		Expense meal = expenseRepository.save(Expense.create(round, host, 50_000L, "고기"));
		GameSession ladder = saveRevealedGame(GameSession.create(round, GameType.LADDER, host.getId(),
				PenaltyKind.MONEY, "꽝이 고기값", 1));
		Penalty minsuPenalty = penaltyRepository.save(Penalty.assign(ladder, minsu));
		minsuPenalty.apply();
		PenaltyRule rule = penaltyRuleRepository.save(PenaltyRule.create(ladder, PenaltyMoneyType.FIXED_AMOUNT,
				10_000L, null, meal, host.getId(), AT));
		Settlement settlement = settlementRepository.findByMeetingId(meeting.getId()).orElseThrow();
		saveRevision(settlement, minsuPenalty, rule, 10_000L);
		settlement.reopen();
		saveRevision(settlement, minsuPenalty, rule, 12_000L);
		saveActionRerun(round);
		saveBoardGame(round, minsu, minsu, younghee);
		BoardGameRecord beforeLoser = BoardGameRecord.create(round, "옛 기록", host.getId(),
				List.of(BoardGameRank.of(host.getId(), 1), BoardGameRank.of(younghee.getId(), 2)), younghee.getId());
		ReflectionTestUtils.setField(beforeLoser, "loserMemberId", null);
		boardGameRecordRepository.save(beforeLoser);
		return meeting;
	}

	/**
	 * 2020년 6월: 영희·지수 참석, 정산 미확정. 금액형 꽝뽑기 영희 꽝 → 면제. 보드게임 꼴등 영희.
	 */
	private Meeting saveJune2020() {
		Meeting meeting = savePastMeeting("6월 모임", LocalDateTime.of(2020, 6, 1, 18, 0));
		Round round = roundRepository.save(Round.create(meeting, 1, "보드게임"));
		respond(round, younghee, AttendanceStatus.ATTEND);
		respond(round, jisu, AttendanceStatus.ATTEND);
		respond(round, host, AttendanceStatus.UNDECIDED);
		GameSession draw = saveRevealedGame(GameSession.create(round, GameType.DRAW, host.getId(),
				PenaltyKind.MONEY, null, 1));
		penaltyRepository.save(Penalty.assign(draw, younghee)).waive(host.getId(), AT);
		saveBoardGame(round, younghee, younghee, host);
		return meeting;
	}

	// 다가오는 모임·날짜 투표 중 모임·다른 그룹 모임의 벌칙·참석은 세지 않는다
	private void saveExcludedMeetings() {
		Round upcoming = roundRepository.save(Round.create(
				savePastMeeting("다가오는 모임", LocalDateTime.of(2099, 1, 1, 18, 0)), 1, "밥"));
		respond(upcoming, jisu, AttendanceStatus.ATTEND);
		penaltyRepository.save(Penalty.assign(saveRevealedGame(GameSession.create(upcoming, GameType.LADDER,
				host.getId(), PenaltyKind.ACTION, null, 1)), jisu));
		Meeting voting = meetingRepository.save(Meeting.createWithDateVote(group, "날짜 투표", null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(voting));
		Group other = groupRepository.save(Group.create("다른 그룹"));
		Meeting otherMeeting = meetingRepository.save(
				Meeting.create(other, "남의 모임", LocalDateTime.of(2020, 6, 1, 18, 0), null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(otherMeeting));
		Round otherRound = roundRepository.save(Round.create(otherMeeting, 1, "밥"));
		respond(otherRound, minsu, AttendanceStatus.ATTEND);
		penaltyRepository.save(Penalty.assign(saveRevealedGame(GameSession.create(otherRound, GameType.LADDER,
				host.getId(), PenaltyKind.ACTION, null, 1)), minsu));
	}

	private void saveActionRerun(Round round) {
		GameSession original = saveRevealedGame(GameSession.create(round, GameType.DRAW, host.getId(),
				PenaltyKind.ACTION, "노래 한 곡", 1));
		penaltyRepository.save(Penalty.assign(original, host)).voidOut();
		GameSession rerun = saveRevealedGame(GameSession.rerunOf(original, host.getId()));
		penaltyRepository.save(Penalty.assign(rerun, younghee));
	}

	private void saveRevision(Settlement settlement, Penalty penalty, PenaltyRule rule, long amount) {
		int revisionNo = settlement.confirm(AT);
		SettlementRevision revision = settlementRevisionRepository.save(
				SettlementRevision.create(settlement, revisionNo, host, AT, false));
		penaltyChargeRepository.save(PenaltyCharge.create(revision, penalty, amount, rule));
	}

	private void saveBoardGame(Round round, Member loser, Member... participants) {
		List<BoardGameRank> ranks = new ArrayList<>();
		for (int i = 0; i < participants.length; i++) {
			ranks.add(BoardGameRank.of(participants[i].getId(), participants.length - i));
		}
		boardGameRecordRepository.save(BoardGameRecord.create(round, "스플렌더", host.getId(), ranks, loser.getId()));
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

	private Meeting savePastMeeting(String title, LocalDateTime startAt) {
		Meeting meeting = meetingRepository.save(Meeting.create(group, title, startAt, null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(meeting));
		return meeting;
	}

	private void respond(Round round, Member member, AttendanceStatus status) {
		attendanceRepository.save(Attendance.respond(round, member, status, AT));
	}

	private Member joinGroup(Group target, String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(target, member, nickname, GroupRole.MEMBER));
		return member;
	}
}
