package com.poco7250.moyeo.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import com.poco7250.moyeo.history.dto.YearReviewResponse;
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
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
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
 * 연말 결산(PRD H-4) 통합 테스트. 결산 연도는 2019년, 다가오는 모임은 2099년으로 둬 실제 시계와 상관없이 구분한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class YearReviewIntegrationTest {

	private static final LocalDateTime AT = LocalDateTime.of(2019, 4, 20, 12, 0);

	private static long nextKakaoId = 980_000L;

	@Autowired
	YearReviewService yearReviewService;

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

	private Group group;
	private Member host;
	private Member minsu;
	private Member younghee;
	private Member jisu;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = joinGroup(group, "호스트");
		minsu = joinGroup(group, "민수");
		younghee = joinGroup(group, "영희");
		jisu = joinGroup(group, "지수");
		saveMarch2019();
		saveJune2019();
		saveExcludedMeetings();
	}

	@Test
	@DisplayName("그해 지난 모임만 세어 모임·차수 수, 현재 지출 합계, 확정 모임 수와 공유 문구를 준다")
	void summary() {
		YearReviewResponse result = yearReviewService.get(group.getId(), jisu.getId(), 2019);

		assertThat(result.year()).isEqualTo(2019);
		assertThat(result.meetingCount()).isEqualTo(2);
		assertThat(result.roundCount()).isEqualTo(3);
		assertThat(result.totalExpense()).isEqualTo(100_000L);
		assertThat(result.confirmedMeetingCount()).isEqualTo(1);
		assertThat(result.shareText()).isEqualTo("""
				[대학 동기] 2019년 결산
				올해 모임 2번 · 총 100,000원
				최다 참석: 영희(2번)
				가장 많이 간 곳: 강남 고깃집(2번)
				벌칙왕: 민수·영희(1번)
				노쇼왕: 지수(1번)""");
	}

	@Test
	@DisplayName("참석은 모임 단위로 세고, 부담액은 마지막 확정 리비전 기준이며 멤버 전원이 경쟁 순위로 나온다")
	void rankings() {
		YearReviewResponse result = yearReviewService.get(group.getId(), host.getId(), 2019);

		assertThat(result.attendanceRanking())
				.extracting(r -> r.member().nickname(), r -> r.rank(), r -> r.attendCount())
				.containsExactly(tuple("영희", 1, 2L), tuple("호스트", 2, 1L), tuple("민수", 2, 1L),
						tuple("지수", 2, 1L));
		assertThat(result.shareRanking())
				.extracting(r -> r.member().nickname(), r -> r.rank(), r -> r.shareAmount())
				.containsExactly(tuple("민수", 1, 35_000L), tuple("호스트", 2, 25_000L), tuple("영희", 3, 20_000L),
						tuple("지수", 4, 0L));
	}

	@Test
	@DisplayName("장소는 앞뒤 공백을 뺀 이름으로 묶어 방문 횟수 순으로, 벌칙왕은 무효를 뺀 1위 전원, 노쇼왕은 노쇼 1위 전원이다")
	void placesAndPenaltyKings() {
		YearReviewResponse result = yearReviewService.get(group.getId(), host.getId(), 2019);

		assertThat(result.topPlaces()).extracting(p -> p.rank(), p -> p.label(), p -> p.visitCount(),
				p -> p.lastVisitedAt())
				.containsExactly(tuple(1, "강남 고깃집", 2L, LocalDateTime.of(2019, 3, 1, 18, 0)),
						tuple(2, "홍대 포차", 1L, LocalDateTime.of(2019, 6, 1, 18, 0)));
		assertThat(result.penaltyKings()).extracting(k -> k.member().nickname(), k -> k.penaltyCount())
				.containsExactly(tuple("민수", 1L), tuple("영희", 1L));
		assertThat(result.noShowKings()).extracting(k -> k.member().nickname(), k -> k.noShowCount())
				.containsExactly(tuple("지수", 1L));
	}

	@Test
	@DisplayName("모임이 없는 연도면 숫자는 0, 장소·벌칙왕은 비고 공유 문구는 null이다. 순위는 멤버 전원 0으로 나온다")
	void emptyYear() {
		YearReviewResponse result = yearReviewService.get(group.getId(), host.getId(), 2017);

		assertThat(result.meetingCount()).isZero();
		assertThat(result.roundCount()).isZero();
		assertThat(result.totalExpense()).isZero();
		assertThat(result.topPlaces()).isEmpty();
		assertThat(result.penaltyKings()).isEmpty();
		assertThat(result.noShowKings()).isEmpty();
		assertThat(result.shareText()).isNull();
		assertThat(result.attendanceRanking()).extracting(r -> r.rank(), r -> r.attendCount())
				.containsOnly(tuple(1, 0L));
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER")
	void rejectsNonMember() {
		Member outsider = memberRepository.save(Member.ofKakao(nextKakaoId++, "외부인", null, null));

		assertThatThrownBy(() -> yearReviewService.get(group.getId(), outsider.getId(), 2019))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}

	/**
	 * 2019년 3월 2차 모임: 1차(호스트·민수)·2차(민수·영희, 지수 노쇼) 모두 "강남 고깃집"(2차는 공백 섞어 입력), 지출 50,000·30,000원.
	 * 리비전 1 확정 → 재확정 리비전 2(호스트 25,000·민수 35,000·영희 20,000). 금액형 사다리 민수 꽝, 호스트 벌칙은 무효.
	 */
	private void saveMarch2019() {
		Meeting meeting = savePastMeeting(group, "3월 모임", LocalDateTime.of(2019, 3, 1, 18, 0));
		Round meal = saveRoundWithPlace(meeting, 1, "강남 고깃집");
		Round second = saveRoundWithPlace(meeting, 2, "  강남 고깃집 ");
		respond(meal, host, AttendanceStatus.ATTEND);
		respond(meal, minsu, AttendanceStatus.ATTEND);
		respond(second, minsu, AttendanceStatus.ATTEND);
		respond(second, younghee, AttendanceStatus.ATTEND);
		saveNoShow(second, jisu);
		expenseRepository.save(Expense.create(meal, host, 50_000L, "고기"));
		expenseRepository.save(Expense.create(second, minsu, 30_000L, "맥주"));
		Settlement settlement = settlementRepository.findByMeetingId(meeting.getId()).orElseThrow();
		saveRevision(settlement, 30_000L, 30_000L, 20_000L);
		settlement.reopen();
		saveRevision(settlement, 25_000L, 35_000L, 20_000L);
		GameSession ladder = saveRevealedGame(GameSession.create(meal, GameType.LADDER, host.getId(),
				PenaltyKind.MONEY, null, 1));
		penaltyRepository.save(Penalty.assign(ladder, minsu)).apply();
		GameSession canceledByRerun = saveRevealedGame(GameSession.create(second, GameType.DRAW, host.getId(),
				PenaltyKind.ACTION, null, 1));
		penaltyRepository.save(Penalty.assign(canceledByRerun, host)).voidOut();
	}

	/**
	 * 2019년 6월: "홍대 포차", 영희·지수 참석, 지출 20,000원, 정산 미확정. 행동형 꽝뽑기 영희 꽝.
	 */
	private void saveJune2019() {
		Meeting meeting = savePastMeeting(group, "6월 모임", LocalDateTime.of(2019, 6, 1, 18, 0));
		Round round = saveRoundWithPlace(meeting, 1, "홍대 포차");
		respond(round, younghee, AttendanceStatus.ATTEND);
		respond(round, jisu, AttendanceStatus.ATTEND);
		expenseRepository.save(Expense.create(round, younghee, 20_000L, "안주"));
		GameSession draw = saveRevealedGame(GameSession.create(round, GameType.DRAW, host.getId(),
				PenaltyKind.ACTION, "다음 장소 예약", 1));
		penaltyRepository.save(Penalty.assign(draw, younghee));
	}

	// 다른 해·다가오는·날짜 투표 중·다른 그룹 모임은 세지 않는다
	private void saveExcludedMeetings() {
		saveSimpleMeeting(group, "작년 모임", LocalDateTime.of(2018, 12, 31, 23, 0));
		saveSimpleMeeting(group, "다가오는 모임", LocalDateTime.of(2099, 1, 1, 18, 0));
		Meeting voting = meetingRepository.save(Meeting.createWithDateVote(group, "날짜 투표", null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(voting));
		saveSimpleMeeting(groupRepository.save(Group.create("다른 그룹")), "남의 모임",
				LocalDateTime.of(2019, 6, 1, 18, 0));
	}

	// 홍대 포차·지수 참석·지출·지수 벌칙이 있는 모임
	private void saveSimpleMeeting(Group target, String title, LocalDateTime startAt) {
		Round round = saveRoundWithPlace(savePastMeeting(target, title, startAt), 1, "홍대 포차");
		respond(round, jisu, AttendanceStatus.ATTEND);
		expenseRepository.save(Expense.create(round, jisu, 99_999L, "제외"));
		penaltyRepository.save(Penalty.assign(saveRevealedGame(GameSession.create(round, GameType.LADDER,
				host.getId(), PenaltyKind.ACTION, null, 1)), jisu));
	}

	private void saveRevision(Settlement settlement, long hostShare, long minsuShare, long youngheeShare) {
		int revisionNo = settlement.confirm(AT);
		SettlementRevision revision = settlementRevisionRepository.save(
				SettlementRevision.create(settlement, revisionNo, host, AT, false));
		revisionMemberShareRepository.save(RevisionMemberShare.create(revision, host, hostShare, 0L));
		revisionMemberShareRepository.save(RevisionMemberShare.create(revision, minsu, minsuShare, 0L));
		revisionMemberShareRepository.save(RevisionMemberShare.create(revision, younghee, youngheeShare, 0L));
	}

	private Round saveRoundWithPlace(Meeting meeting, int seq, String label) {
		Round round = roundRepository.save(Round.create(meeting, seq, seq + "차"));
		round.confirmDirectly(placeCandidateRepository.save(
				PlaceCandidate.propose(round, Place.of(label, null, null), host)));
		return round;
	}

	private GameSession saveRevealedGame(GameSession game) {
		GameSession saved = gameSessionRepository.save(game);
		saved.markAsRoot();
		saved.start();
		saved.reveal(AT);
		return saved;
	}

	private Meeting savePastMeeting(Group target, String title, LocalDateTime startAt) {
		Meeting meeting = meetingRepository.save(Meeting.create(target, title, startAt, null, null, host.getId()));
		settlementRepository.save(Settlement.createDraft(meeting));
		return meeting;
	}

	private void respond(Round round, Member member, AttendanceStatus status) {
		attendanceRepository.save(Attendance.respond(round, member, status, AT));
	}

	// 참석으로 응답했다가 실제 참석자 확정 때 체크인이 없어 불참이 된 노쇼 행
	private void saveNoShow(Round round, Member member) {
		Attendance attendance = Attendance.respond(round, member, AttendanceStatus.ATTEND, AT);
		attendance.recordNoShowIfMissed();
		attendance.changeStatus(AttendanceStatus.ABSENT, AT);
		attendanceRepository.save(attendance);
	}

	private Member joinGroup(Group target, String nickname) {
		Member member = memberRepository.save(Member.ofKakao(nextKakaoId++, nickname, null, null));
		groupMemberRepository.save(GroupMember.join(target, member, nickname, GroupRole.MEMBER));
		return member;
	}
}
