package com.poco7250.moyeo.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.global.auth.GameAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleRequest;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleSnapshot;
import com.poco7250.moyeo.penalty.dto.PenaltyWaiverSnapshot;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import com.poco7250.moyeo.settlement.calc.ExpenseInput;
import com.poco7250.moyeo.settlement.calc.PenalizedMember;
import com.poco7250.moyeo.settlement.calc.PenaltyChargeCalculator;
import com.poco7250.moyeo.settlement.calc.PenaltyChargeResult;
import com.poco7250.moyeo.settlement.calc.PenaltyRuleInput;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PenaltyConditionServiceTest {

	private static final Long GAME_ID = 300L;
	private static final Long MEETING_ID = 50L;
	private static final Long OTHER_MEETING_ID = 51L;
	private static final Long RULE_ID = 700L;
	private static final Long BOWLING_ID = 900L;
	private static final Long DINNER_ID = 901L;
	private static final long BOWLING_AMOUNT = 87_650L;
	private static final Long HOST_ID = 4L;
	private static final Long CHEOLSU_ID = 1L;
	private static final Long MINSU_ID = 2L;
	private static final Long YEONGHEE_ID = 3L;
	private static final Long MINSU_PENALTY_ID = 11L;
	private static final Long YEONGHEE_PENALTY_ID = 12L;
	private static final List<Long> PENALTY_MEMBER_IDS = List.of(MINSU_ID, YEONGHEE_ID);
	private static final LocalDateTime REVEALED_AT = LocalDateTime.of(2026, 12, 20, 21, 0);
	private static final LocalDateTime OLD_SET_AT = LocalDateTime.of(2026, 12, 20, 21, 30);
	private static final LocalDateTime OLD_WAIVED_AT = LocalDateTime.of(2026, 12, 20, 21, 40);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 22, 0);
	private static final PenaltyRuleRequest RATIO_30 = new PenaltyRuleRequest(PenaltyMoneyType.RATIO, BOWLING_ID, null,
			30);

	@Mock
	GameAccessChecker gameAccessChecker;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	PenaltyRepository penaltyRepository;

	@Mock
	PenaltyRuleRepository penaltyRuleRepository;

	@Mock
	ExpenseRepository expenseRepository;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	PenaltyConditionService service;

	GameSession game;
	List<Penalty> penalties;

	@BeforeEach
	void setUp() {
		service = new PenaltyConditionService(
				new PenaltyConditionLoader(gameAccessChecker, settlementGuard, penaltyRepository),
				penaltyRuleRepository, expenseRepository, settlementGuard, changeLogRecorder,
				TestClocks.fixed(NOW));
		game = revealedGame(PenaltyKind.MONEY);
		penalties = List.of(penalty(MINSU_PENALTY_ID, MINSU_ID), penalty(YEONGHEE_PENALTY_ID, YEONGHEE_ID));
		given(gameAccessChecker.requireHost(GAME_ID, HOST_ID)).willAnswer(invocation -> game);
		given(penaltyRepository.findAllByGameSessionIdAndStatusNot(GAME_ID, PenaltyStatus.VOIDED))
				.willAnswer(invocation -> penalties);
		given(penaltyRuleRepository.save(any())).willAnswer(invocation -> {
			PenaltyRule rule = invocation.getArgument(0);
			ReflectionTestUtils.setField(rule, "id", RULE_ID);
			return rule;
		});
		givenExpense(BOWLING_ID, MEETING_ID);
	}

	@Test
	@DisplayName("ASSIGNED 게임에 RATIO 30%를 설정하면 규칙 1건이 생기고 두 벌칙이 APPLIED가 되며 각자 26,300원이다(예 5-a)")
	void setRuleOnAssigned() {
		PenaltyConditionResponse response = service.setRule(GAME_ID, HOST_ID, RATIO_30);

		PenaltyRule saved = savedRule();
		assertThat(penalties).extracting(Penalty::getStatus).containsOnly(PenaltyStatus.APPLIED);
		assertThat(response.status()).isEqualTo(PenaltyStatus.APPLIED);
		assertThat(response.penaltyMemberIds()).isEqualTo(PENALTY_MEMBER_IDS);
		assertThat(response.rule().targetExpenseId()).isEqualTo(BOWLING_ID);
		assertThat(response.rule().setBy()).isEqualTo(HOST_ID);
		assertThat(response.rule().updatedAt()).isEqualTo(NOW);
		assertThat(chargesOf(saved)).extracting(PenaltyChargeResult::amount).containsExactly(26_300L, 26_300L);
		PenaltyRuleSnapshot after = ratioSnapshot(30, BOWLING_ID, NOW);
		// 변경 로그는 변경 후 정산 상태를 담으므로 REOPENED 전이 뒤에 남아야 한다
		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY_RULE, RULE_ID, ChangeType.CREATE)));
		order.verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY_RULE, RULE_ID,
				ChangeType.CREATE, null, after);
		order.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("APPLIED 게임에 조건을 다시 설정하면 규칙 값만 바뀌고 전/후 값이 변경 로그 1건으로 남는다")
	void setRuleOnAppliedUpdatesRule() {
		PenaltyRule rule = givenApplied();
		givenExpense(DINNER_ID, MEETING_ID);

		PenaltyConditionResponse response = service.setRule(GAME_ID, HOST_ID,
				new PenaltyRuleRequest(PenaltyMoneyType.FIXED_AMOUNT, DINNER_ID, 20_000L, null));

		assertThat(rule.getMoneyType()).isEqualTo(PenaltyMoneyType.FIXED_AMOUNT);
		assertThat(rule.getAmount()).isEqualTo(20_000L);
		assertThat(rule.getRatioPct()).isNull();
		assertThat(penalties).extracting(Penalty::getStatus).containsOnly(PenaltyStatus.APPLIED);
		assertThat(response.rule().targetExpenseId()).isEqualTo(DINNER_ID);
		verify(penaltyRuleRepository, never()).save(any());
		verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY_RULE, RULE_ID, ChangeType.UPDATE)));
		verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY_RULE, RULE_ID,
				ChangeType.UPDATE, ratioSnapshot(30, BOWLING_ID, OLD_SET_AT),
				new PenaltyRuleSnapshot(GAME_ID, PenaltyMoneyType.FIXED_AMOUNT, 20_000L, null, DINNER_ID, HOST_ID,
						NOW));
	}

	@Test
	@DisplayName("WAIVED 게임에 조건을 설정하면 면제가 취소되고 규칙이 생겨 전원 APPLIED가 되며 변경 로그가 2건 남는다(E2)")
	void setRuleOnWaived() {
		givenWaived();

		PenaltyConditionResponse response = service.setRule(GAME_ID, HOST_ID, RATIO_30);

		assertThat(penalties).allSatisfy(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.APPLIED);
			assertThat(penalty.getWaivedBy()).isNull();
			assertThat(penalty.getWaivedAt()).isNull();
		});
		assertThat(response.status()).isEqualTo(PenaltyStatus.APPLIED);
		assertThat(response.waivedBy()).isNull();
		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY, GAME_ID, ChangeType.UPDATE),
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY_RULE, RULE_ID, ChangeType.CREATE)));
		order.verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY, GAME_ID,
				ChangeType.UPDATE, waivedSnapshot(OLD_WAIVED_AT), notWaivedSnapshot());
		order.verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY_RULE, RULE_ID,
				ChangeType.CREATE, null, ratioSnapshot(30, BOWLING_ID, NOW));
		order.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("조건을 해제하면 규칙이 삭제되고 전원 ASSIGNED로 돌아가며 삭제 전 값이 변경 로그에 남는다")
	void clearRule() {
		PenaltyRule rule = givenApplied();

		PenaltyConditionResponse response = service.clearRule(GAME_ID, HOST_ID);

		verify(penaltyRuleRepository).delete(rule);
		assertThat(penalties).extracting(Penalty::getStatus).containsOnly(PenaltyStatus.ASSIGNED);
		assertThat(response.status()).isEqualTo(PenaltyStatus.ASSIGNED);
		assertThat(response.rule()).isNull();
		verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY_RULE, RULE_ID, ChangeType.DELETE)));
		verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY_RULE, RULE_ID,
				ChangeType.DELETE, ratioSnapshot(30, BOWLING_ID, OLD_SET_AT), null);
	}

	@Test
	@DisplayName("조건이 없는 ASSIGNED 게임의 조건 해제는 409다")
	void clearRuleWithoutRule() {
		assertRejected(() -> service.clearRule(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("ASSIGNED 게임에서 금액 벌칙 없음을 고르면 전원 WAIVED가 되고 면제자와 면제 시각이 기록된다")
	void waiveOnAssigned() {
		PenaltyConditionResponse response = service.waive(GAME_ID, HOST_ID);

		assertThat(penalties).allSatisfy(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.WAIVED);
			assertThat(penalty.getWaivedBy()).isEqualTo(HOST_ID);
			assertThat(penalty.getWaivedAt()).isEqualTo(NOW);
		});
		assertThat(response.status()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(response.waivedBy()).isEqualTo(HOST_ID);
		assertThat(response.waivedAt()).isEqualTo(NOW);
		verify(penaltyRuleRepository, never()).delete(any());
		verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY, GAME_ID, ChangeType.UPDATE)));
		verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY, GAME_ID,
				ChangeType.UPDATE, notWaivedSnapshot(), waivedSnapshot(NOW));
	}

	@Test
	@DisplayName("APPLIED 게임에서 금액 벌칙 없음을 고르면 규칙이 삭제되고 전원 WAIVED가 되며 변경 로그가 2건 남는다(E2)")
	void waiveOnApplied() {
		PenaltyRule rule = givenApplied();

		PenaltyConditionResponse response = service.waive(GAME_ID, HOST_ID);

		verify(penaltyRuleRepository).delete(rule);
		assertThat(penalties).extracting(Penalty::getStatus).containsOnly(PenaltyStatus.WAIVED);
		assertThat(response.rule()).isNull();
		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY_RULE, RULE_ID, ChangeType.DELETE),
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY, GAME_ID, ChangeType.UPDATE)));
		order.verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY_RULE, RULE_ID,
				ChangeType.DELETE, ratioSnapshot(30, BOWLING_ID, OLD_SET_AT), null);
		order.verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY, GAME_ID,
				ChangeType.UPDATE, notWaivedSnapshot(), waivedSnapshot(NOW));
		order.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("이미 WAIVED인 게임을 다시 면제하면 409다")
	void waiveOnWaived() {
		givenWaived();

		assertRejected(() -> service.waive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("면제를 취소하면 전원 ASSIGNED로 돌아가고 면제 정보가 비워지며 전 값이 변경 로그에 남는다")
	void unwaive() {
		givenWaived();

		PenaltyConditionResponse response = service.unwaive(GAME_ID, HOST_ID);

		assertThat(penalties).allSatisfy(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.ASSIGNED);
			assertThat(penalty.getWaivedBy()).isNull();
			assertThat(penalty.getWaivedAt()).isNull();
		});
		assertThat(response.status()).isEqualTo(PenaltyStatus.ASSIGNED);
		assertThat(response.waivedBy()).isNull();
		verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY, GAME_ID, ChangeType.UPDATE)));
		verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY, GAME_ID,
				ChangeType.UPDATE, waivedSnapshot(OLD_WAIVED_AT), notWaivedSnapshot());
	}

	@Test
	@DisplayName("면제 상태가 아닌 게임의 면제 취소는 409다")
	void unwaiveOnAssigned() {
		assertRejected(() -> service.unwaive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("없는 지출을 대상으로 고르면 404 EXPENSE_NOT_FOUND다")
	void expenseNotFound() {
		given(expenseRepository.findWithRoundById(BOWLING_ID)).willReturn(Optional.empty());

		assertRejected(() -> service.setRule(GAME_ID, HOST_ID, RATIO_30), ErrorCode.EXPENSE_NOT_FOUND);

		assertNothingChanged();
	}

	@Test
	@DisplayName("다른 모임의 지출을 대상으로 고르면 400 EXPENSE_NOT_IN_MEETING이다")
	void expenseInOtherMeeting() {
		givenExpense(BOWLING_ID, OTHER_MEETING_ID);

		assertRejected(() -> service.setRule(GAME_ID, HOST_ID, RATIO_30), ErrorCode.EXPENSE_NOT_IN_MEETING);

		assertNothingChanged();
	}

	@Test
	@DisplayName("호스트가 아니면 403 NOT_MEETING_HOST이고 정산을 잠그지 않는다")
	void notHost() {
		given(gameAccessChecker.requireHost(GAME_ID, MINSU_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		assertRejected(() -> service.setRule(GAME_ID, MINSU_ID, RATIO_30), ErrorCode.NOT_MEETING_HOST);
		assertRejected(() -> service.clearRule(GAME_ID, MINSU_ID), ErrorCode.NOT_MEETING_HOST);
		assertRejected(() -> service.waive(GAME_ID, MINSU_ID), ErrorCode.NOT_MEETING_HOST);
		assertRejected(() -> service.unwaive(GAME_ID, MINSU_ID), ErrorCode.NOT_MEETING_HOST);

		verifyNoInteractions(settlementGuard, penaltyRepository, changeLogRecorder);
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyKind.class, names = {"NONE", "ACTION"})
	@DisplayName("금액형이 아닌 게임은 409 PENALTY_NOT_CONFIGURABLE이다")
	void notMoneyGame(PenaltyKind penaltyKind) {
		game = revealedGame(penaltyKind);

		assertRejected(() -> service.setRule(GAME_ID, HOST_ID, RATIO_30), ErrorCode.PENALTY_NOT_CONFIGURABLE);
		assertRejected(() -> service.waive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("공개 전(WAITING·PLAYING)이거나 취소된 게임은 409 PENALTY_NOT_CONFIGURABLE이다")
	void notRevealedGame() {
		GameSession waiting = newGame(PenaltyKind.MONEY);
		GameSession playing = newGame(PenaltyKind.MONEY);
		playing.start();
		GameSession canceled = newGame(PenaltyKind.MONEY);
		canceled.cancel();

		for (GameSession notRevealed : List.of(waiting, playing, canceled)) {
			game = notRevealed;
			assertRejected(() -> service.setRule(GAME_ID, HOST_ID, RATIO_30), ErrorCode.PENALTY_NOT_CONFIGURABLE);
			assertRejected(() -> service.waive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);
		}

		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("다시 돌리기로 벌칙이 전부 VOIDED면 409 PENALTY_NOT_CONFIGURABLE이다")
	void voidedPenalties() {
		penalties = List.of();

		assertRejected(() -> service.setRule(GAME_ID, HOST_ID, RATIO_30), ErrorCode.PENALTY_NOT_CONFIGURABLE);
		assertRejected(() -> service.waive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		assertNothingChanged();
	}

	@Test
	@DisplayName("CLOSED 게임도 REVEALED와 똑같이 조건을 설정할 수 있다")
	void closedGame() {
		game.close();

		PenaltyConditionResponse response = service.setRule(GAME_ID, HOST_ID, RATIO_30);

		assertThat(response.status()).isEqualTo(PenaltyStatus.APPLIED);
		assertThat(penalties).extracting(Penalty::getStatus).containsOnly(PenaltyStatus.APPLIED);
	}

	private void assertNothingChanged() {
		verify(penaltyRuleRepository, never()).save(any());
		verify(settlementGuard, never()).markAmountChanged(any(), any(), any());
		verifyNoInteractions(changeLogRecorder);
	}

	private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
		assertThatThrownBy(action)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(expected);
	}

	/**
	 * 저장된 규칙을 정산 계산 입력으로 바꿔 볼링 지출의 벌칙 부담액을 구한다(결제자 철수, 참석자 4명).
	 */
	private List<PenaltyChargeResult> chargesOf(PenaltyRule rule) {
		List<PenalizedMember> penalized = penalties.stream()
				.map(penalty -> new PenalizedMember(penalty.getId(), penalty.getMember().getId()))
				.toList();
		PenaltyRuleInput input = new PenaltyRuleInput(BOWLING_ID, rule.getMoneyType(), rule.getAmount(),
				rule.getRatioPct(), game.getApplyOrderAt(), GAME_ID, penalized);
		ExpenseInput expense = new ExpenseInput(BOWLING_ID, BOWLING_AMOUNT, CHEOLSU_ID,
				Set.of(CHEOLSU_ID, MINSU_ID, YEONGHEE_ID, HOST_ID), List.of());
		return PenaltyChargeCalculator.calculate(expense, List.of(input)).charges();
	}

	private PenaltyRule savedRule() {
		org.mockito.ArgumentCaptor<PenaltyRule> captor = org.mockito.ArgumentCaptor.forClass(PenaltyRule.class);
		verify(penaltyRuleRepository).save(captor.capture());
		return captor.getValue();
	}

	private PenaltyRule givenApplied() {
		penalties.forEach(Penalty::apply);
		Expense bowling = expenseRepository.findWithRoundById(BOWLING_ID).orElseThrow();
		PenaltyRule rule = PenaltyRule.create(game, PenaltyMoneyType.RATIO, null, 30, bowling, HOST_ID, OLD_SET_AT);
		ReflectionTestUtils.setField(rule, "id", RULE_ID);
		given(penaltyRuleRepository.findByGameSessionId(GAME_ID)).willReturn(Optional.of(rule));
		return rule;
	}

	private void givenWaived() {
		penalties.forEach(penalty -> penalty.waive(HOST_ID, OLD_WAIVED_AT));
	}

	private void givenExpense(Long expenseId, Long meetingId) {
		Meeting meeting = mock(Meeting.class);
		given(meeting.getId()).willReturn(meetingId);
		Round round = mock(Round.class);
		given(round.getMeeting()).willReturn(meeting);
		Expense expense = mock(Expense.class);
		given(expense.getId()).willReturn(expenseId);
		given(expense.getRound()).willReturn(round);
		given(expenseRepository.findWithRoundById(expenseId)).willReturn(Optional.of(expense));
	}

	private PenaltyRuleSnapshot ratioSnapshot(int ratioPct, Long expenseId, LocalDateTime setAt) {
		return new PenaltyRuleSnapshot(GAME_ID, PenaltyMoneyType.RATIO, null, ratioPct, expenseId, HOST_ID, setAt);
	}

	private PenaltyWaiverSnapshot waivedSnapshot(LocalDateTime waivedAt) {
		return PenaltyWaiverSnapshot.waived(GAME_ID, PENALTY_MEMBER_IDS, HOST_ID, waivedAt);
	}

	private PenaltyWaiverSnapshot notWaivedSnapshot() {
		return PenaltyWaiverSnapshot.notWaived(GAME_ID, PENALTY_MEMBER_IDS);
	}

	private GameSession revealedGame(PenaltyKind penaltyKind) {
		GameSession revealed = newGame(penaltyKind);
		revealed.start();
		revealed.reveal(REVEALED_AT);
		return revealed;
	}

	private GameSession newGame(PenaltyKind penaltyKind) {
		Meeting meeting = mock(Meeting.class);
		given(meeting.getId()).willReturn(MEETING_ID);
		Round round = mock(Round.class);
		given(round.getMeeting()).willReturn(meeting);
		GameSession created = GameSession.create(round, GameType.LADDER, MINSU_ID, penaltyKind, null, 2);
		ReflectionTestUtils.setField(created, "id", GAME_ID);
		return created;
	}

	private Penalty penalty(Long id, Long memberId) {
		Member member = Member.ofKakao(memberId + 1000, "회원" + memberId, null, null);
		ReflectionTestUtils.setField(member, "id", memberId);
		Penalty penalty = Penalty.assign(game, member);
		ReflectionTestUtils.setField(penalty, "id", id);
		return penalty;
	}
}
