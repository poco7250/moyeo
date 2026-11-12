package com.poco7250.moyeo.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameChangeSnapshot;
import com.poco7250.moyeo.game.dto.GameRerunRevealSnapshot;
import com.poco7250.moyeo.game.dto.GameRevealSnapshot;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleSnapshot;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PenaltyAssignerTest {

	private static final Long GAME_ID = 300L;
	private static final Long RERUN_ID = 301L;
	private static final Long RULE_ID = 700L;
	private static final Long EXPENSE_ID = 900L;
	private static final Long HOST_ID = 4L;
	private static final LocalDateTime RULE_SET_AT = LocalDateTime.of(2026, 12, 20, 22, 0);
	private static final LocalDateTime WAIVED_AT = LocalDateTime.of(2026, 12, 20, 22, 30);
	private static final LocalDateTime RERUN_REVEALED_AT = LocalDateTime.of(2026, 12, 20, 23, 0);
	private static final Long MEETING_ID = 50L;
	private static final Long ACTOR_ID = 1L;
	private static final Long MINSU_ID = 2L;
	private static final Long YEONGHEE_ID = 3L;
	private static final LocalDateTime REVEALED_AT = LocalDateTime.of(2026, 12, 20, 21, 0);

	@Mock
	PenaltyRepository penaltyRepository;

	@Mock
	PenaltyRuleRepository penaltyRuleRepository;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	@InjectMocks
	PenaltyAssigner penaltyAssigner;

	@Captor
	ArgumentCaptor<List<Penalty>> penaltiesCaptor;

	Round round;

	@BeforeEach
	void setUp() {
		round = mock(Round.class);
	}

	@Test
	@DisplayName("금액형 게임의 벌칙자 2명은 ASSIGNED로 생성되고 정산 반영과 변경 로그 1건이 남는다")
	void moneyGameAssignsAndReflectsToSettlement() {
		GameSession game = revealedGame(PenaltyKind.MONEY);
		givenMeeting();

		penaltyAssigner.onRevealed(game, ACTOR_ID, List.of(member(MINSU_ID), member(YEONGHEE_ID)));

		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).hasSize(2).allSatisfy(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.ASSIGNED);
			assertThat(penalty.getGameSession()).isSameAs(game);
			assertThat(penalty.getWaivedBy()).isNull();
		});
		assertThat(game.getRevealedAt()).isEqualTo(REVEALED_AT);
		assertThat(game.getApplyOrderAt()).isEqualTo(REVEALED_AT);
		// 변경 로그는 변경 후 정산 상태를 담으므로 REOPENED 전이 뒤에 남아야 한다
		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.GAME_SESSION, GAME_ID, ChangeType.UPDATE)));
		order.verify(changeLogRecorder).record(eq(MEETING_ID), eq(ACTOR_ID), eq(ChangeLogEntityType.GAME_SESSION),
				eq(GAME_ID), eq(ChangeType.UPDATE),
				eq(new GameChangeSnapshot(GameStatus.WAITING, GameType.LADDER, PenaltyKind.MONEY)),
				eq(new GameRevealSnapshot(GameStatus.REVEALED, GameType.LADDER, PenaltyKind.MONEY,
						List.of(MINSU_ID, YEONGHEE_ID))));
	}

	@Test
	@DisplayName("벌칙 종류가 없음이면 벌칙을 만들지 않고 정산도 건드리지 않는다")
	void noneKindCreatesNothing() {
		penaltyAssigner.onRevealed(revealedGame(PenaltyKind.NONE), ACTOR_ID, List.of(member(MINSU_ID)));

		verifyNoInteractions(penaltyRepository, penaltyRuleRepository, settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("행동형은 ASSIGNED 벌칙만 만들고 정산 상태와 변경 로그는 건드리지 않는다")
	void actionKindAssignsWithoutSettlement() {
		penaltyAssigner.onRevealed(revealedGame(PenaltyKind.ACTION), ACTOR_ID, List.of(member(MINSU_ID)));

		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).extracting(Penalty::getStatus).containsExactly(PenaltyStatus.ASSIGNED);
		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("벌칙자 입력 순서가 섞여 있어도 회원 id 오름차순으로 저장한다")
	void savesInMemberIdOrder() {
		GameSession game = revealedGame(PenaltyKind.ACTION);

		penaltyAssigner.onRevealed(game, ACTOR_ID, List.of(member(9L), member(MINSU_ID), member(5L)));

		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).extracting(penalty -> penalty.getMember().getId())
				.containsExactly(MINSU_ID, 5L, 9L);
	}

	@Test
	@DisplayName("조건이 적용된 회차를 다시 돌리면 원 벌칙은 VOIDED, 규칙은 새 회차로 옮겨지고 새 벌칙자는 APPLIED다")
	void rerunInheritsAppliedAndMovesRule() {
		GameSession original = revealedGame(PenaltyKind.MONEY);
		GameSession rerun = revealedRerun(original);
		givenMeeting();
		Penalty originalPenalty = penalty(original, 10L, MINSU_ID);
		originalPenalty.apply();
		given(penaltyRepository.findAllByGameSessionIdAndStatusNot(GAME_ID, PenaltyStatus.VOIDED))
				.willReturn(List.of(originalPenalty));
		PenaltyRule rule = ratioRule(original);
		given(penaltyRuleRepository.findByGameSessionId(GAME_ID)).willReturn(Optional.of(rule));

		penaltyAssigner.onRevealed(rerun, ACTOR_ID, List.of(member(YEONGHEE_ID)));

		assertThat(originalPenalty.getStatus()).isEqualTo(PenaltyStatus.VOIDED);
		verify(penaltyRepository, never()).delete(any());
		assertThat(rule.getGameSession()).isSameAs(rerun);
		assertThat(rule.getSetBy()).isEqualTo(HOST_ID);
		assertThat(rule.getUpdatedAt()).isEqualTo(RULE_SET_AT);
		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).singleElement().satisfies(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.APPLIED);
			assertThat(penalty.getGameSession()).isSameAs(rerun);
			assertThat(penalty.getMember().getId()).isEqualTo(YEONGHEE_ID);
		});
		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.GAME_SESSION, RERUN_ID, ChangeType.UPDATE),
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY_RULE, RULE_ID, ChangeType.UPDATE)));
		order.verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.PENALTY_RULE, RULE_ID,
				ChangeType.UPDATE, ruleSnapshot(GAME_ID), ruleSnapshot(RERUN_ID));
		order.verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.GAME_SESSION, RERUN_ID,
				ChangeType.UPDATE, new GameChangeSnapshot(GameStatus.WAITING, GameType.LADDER, PenaltyKind.MONEY),
				new GameRerunRevealSnapshot(GameStatus.REVEALED, GameType.LADDER, PenaltyKind.MONEY,
						List.of(YEONGHEE_ID), GAME_ID, List.of(MINSU_ID), "APPLIED"));
	}

	@Test
	@DisplayName("면제된 회차를 다시 돌리면 새 벌칙자는 면제자와 면제 시각을 이어받은 WAIVED다")
	void rerunInheritsWaived() {
		GameSession original = revealedGame(PenaltyKind.MONEY);
		GameSession rerun = revealedRerun(original);
		givenMeeting();
		Penalty originalPenalty = penalty(original, 10L, MINSU_ID);
		originalPenalty.waive(HOST_ID, WAIVED_AT);
		given(penaltyRepository.findAllByGameSessionIdAndStatusNot(GAME_ID, PenaltyStatus.VOIDED))
				.willReturn(List.of(originalPenalty));
		given(penaltyRuleRepository.findByGameSessionId(GAME_ID)).willReturn(Optional.empty());

		penaltyAssigner.onRevealed(rerun, ACTOR_ID, List.of(member(YEONGHEE_ID)));

		// 원 벌칙의 면제 정보는 이력으로 남는다
		assertThat(originalPenalty.getStatus()).isEqualTo(PenaltyStatus.VOIDED);
		assertThat(originalPenalty.getWaivedBy()).isEqualTo(HOST_ID);
		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).singleElement().satisfies(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.WAIVED);
			assertThat(penalty.getWaivedBy()).isEqualTo(HOST_ID);
			assertThat(penalty.getWaivedAt()).isEqualTo(WAIVED_AT);
		});
	}

	@Test
	@DisplayName("조건이 없던 회차를 다시 돌리면 새 벌칙자는 ASSIGNED고 규칙 이관 기록은 없다")
	void rerunInheritsAssigned() {
		GameSession original = revealedGame(PenaltyKind.MONEY);
		GameSession rerun = revealedRerun(original);
		givenMeeting();
		Penalty originalPenalty = penalty(original, 10L, MINSU_ID);
		given(penaltyRepository.findAllByGameSessionIdAndStatusNot(GAME_ID, PenaltyStatus.VOIDED))
				.willReturn(List.of(originalPenalty));
		given(penaltyRuleRepository.findByGameSessionId(GAME_ID)).willReturn(Optional.empty());

		penaltyAssigner.onRevealed(rerun, ACTOR_ID, List.of(member(YEONGHEE_ID)));

		assertThat(originalPenalty.getStatus()).isEqualTo(PenaltyStatus.VOIDED);
		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).extracting(Penalty::getStatus).containsExactly(PenaltyStatus.ASSIGNED);
		verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.GAME_SESSION, RERUN_ID, ChangeType.UPDATE)));
		verify(changeLogRecorder, never()).record(any(), any(), eq(ChangeLogEntityType.PENALTY_RULE), any(), any(),
				any(), any());
		verify(changeLogRecorder).record(MEETING_ID, ACTOR_ID, ChangeLogEntityType.GAME_SESSION, RERUN_ID,
				ChangeType.UPDATE, new GameChangeSnapshot(GameStatus.WAITING, GameType.LADDER, PenaltyKind.MONEY),
				new GameRerunRevealSnapshot(GameStatus.REVEALED, GameType.LADDER, PenaltyKind.MONEY,
						List.of(YEONGHEE_ID), GAME_ID, List.of(MINSU_ID), "ASSIGNED"));
	}

	@Test
	@DisplayName("원 회차 벌칙자가 새 회차에서도 뽑히면 원 벌칙은 VOIDED로 남고 새 회차 벌칙이 따로 생긴다")
	void rerunSameMemberPenalizedAgain() {
		GameSession original = revealedGame(PenaltyKind.MONEY);
		GameSession rerun = revealedRerun(original);
		givenMeeting();
		Penalty originalPenalty = penalty(original, 10L, MINSU_ID);
		given(penaltyRepository.findAllByGameSessionIdAndStatusNot(GAME_ID, PenaltyStatus.VOIDED))
				.willReturn(List.of(originalPenalty));
		given(penaltyRuleRepository.findByGameSessionId(GAME_ID)).willReturn(Optional.empty());

		penaltyAssigner.onRevealed(rerun, ACTOR_ID, List.of(member(MINSU_ID)));

		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		Penalty created = penaltiesCaptor.getValue().getFirst();
		assertThat(created).isNotSameAs(originalPenalty);
		assertThat(created.getGameSession()).isSameAs(rerun);
		assertThat(created.getMember().getId()).isEqualTo(MINSU_ID);
		assertThat(originalPenalty.getGameSession()).isSameAs(original);
		assertThat(originalPenalty.getStatus()).isEqualTo(PenaltyStatus.VOIDED);
	}

	@Test
	@DisplayName("행동형 다시 돌리기는 원 벌칙을 VOIDED로, 새 벌칙자를 회원 id 순 ASSIGNED로 만들고 정산은 건드리지 않는다")
	void actionRerunVoidsWithoutSettlement() {
		GameSession original = revealedGame(PenaltyKind.ACTION);
		GameSession rerun = revealedRerun(original);
		Penalty first = penalty(original, 10L, MINSU_ID);
		Penalty second = penalty(original, 11L, YEONGHEE_ID);
		given(penaltyRepository.findAllByGameSessionIdAndStatusNot(GAME_ID, PenaltyStatus.VOIDED))
				.willReturn(List.of(first, second));
		given(penaltyRuleRepository.findByGameSessionId(GAME_ID)).willReturn(Optional.empty());

		penaltyAssigner.onRevealed(rerun, ACTOR_ID, List.of(member(9L), member(5L)));

		assertThat(List.of(first, second)).extracting(Penalty::getStatus).containsOnly(PenaltyStatus.VOIDED);
		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).extracting(Penalty::getStatus).containsOnly(PenaltyStatus.ASSIGNED);
		assertThat(penaltiesCaptor.getValue()).extracting(penalty -> penalty.getMember().getId())
				.containsExactly(5L, 9L);
		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	private GameSession revealedRerun(GameSession original) {
		GameSession rerun = GameSession.rerunOf(original, ACTOR_ID);
		ReflectionTestUtils.setField(rerun, "id", RERUN_ID);
		rerun.start();
		rerun.reveal(RERUN_REVEALED_AT);
		return rerun;
	}

	private Penalty penalty(GameSession game, Long id, Long memberId) {
		Penalty penalty = Penalty.assign(game, member(memberId));
		ReflectionTestUtils.setField(penalty, "id", id);
		return penalty;
	}

	private PenaltyRule ratioRule(GameSession game) {
		Expense expense = mock(Expense.class);
		given(expense.getId()).willReturn(EXPENSE_ID);
		PenaltyRule rule = PenaltyRule.create(game, PenaltyMoneyType.RATIO, null, 30, expense, HOST_ID, RULE_SET_AT);
		ReflectionTestUtils.setField(rule, "id", RULE_ID);
		return rule;
	}

	private PenaltyRuleSnapshot ruleSnapshot(Long gameSessionId) {
		return new PenaltyRuleSnapshot(gameSessionId, PenaltyMoneyType.RATIO, null, 30, EXPENSE_ID, HOST_ID,
				RULE_SET_AT);
	}

	private void givenMeeting() {
		Meeting meeting = mock(Meeting.class);
		given(meeting.getId()).willReturn(MEETING_ID);
		given(round.getMeeting()).willReturn(meeting);
	}

	private GameSession revealedGame(PenaltyKind penaltyKind) {
		GameSession game = GameSession.create(round, GameType.LADDER, ACTOR_ID, penaltyKind, null, 2);
		ReflectionTestUtils.setField(game, "id", GAME_ID);
		game.start();
		game.reveal(REVEALED_AT);
		return game;
	}

	private Member member(Long id) {
		Member member = Member.ofKakao(id + 1000, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
