package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.calc.ExpenseInput;
import com.poco7250.moyeo.settlement.calc.PenalizedMember;
import com.poco7250.moyeo.settlement.calc.PenaltyRuleInput;
import com.poco7250.moyeo.settlement.calc.SettlementInput;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.domain.Transfer;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.repository.PenaltyChargeRepository;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import com.poco7250.moyeo.settlement.repository.TransferRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementConfirmServiceTest {

	private static final Long MEETING_ID = 2L;
	private static final Long HOST_ID = 1L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 22, 0);
	private static final MeetingPenalties NO_PENALTIES = MeetingPenalties.empty();
	// 3명이 30,000원을 나누고 1번 회원이 결제했다
	private static final SettlementInput NORMAL_INPUT = new SettlementInput(List.of(
			new ExpenseInput(31L, 30_000L, HOST_ID, Set.of(1L, 2L, 3L), List.of())));
	// 참석자가 없어 NO_SHARE_TARGET 경고가 난다
	private static final SettlementInput WARNING_INPUT = new SettlementInput(List.of(
			new ExpenseInput(31L, 30_000L, HOST_ID, Set.of(), List.of())));

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	SettlementInputAssembler inputAssembler;

	@Mock
	MeetingPenaltyLoader meetingPenaltyLoader;

	@Mock
	SettlementRepository settlementRepository;

	@Mock
	SettlementRevisionRepository revisionRepository;

	@Mock
	RevisionMemberShareRepository memberShareRepository;

	@Mock
	TransferRepository transferRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	GameSessionRepository gameSessionRepository;

	@Mock
	PenaltyChargeRepository penaltyChargeRepository;

	@Captor
	ArgumentCaptor<SettlementRevision> revisionCaptor;

	@Captor
	ArgumentCaptor<List<RevisionMemberShare>> sharesCaptor;

	@Captor
	ArgumentCaptor<List<Transfer>> transfersCaptor;

	@Captor
	ArgumentCaptor<List<PenaltyCharge>> chargesCaptor;

	private SettlementConfirmService service;
	private Settlement settlement;

	@BeforeEach
	void setUp() {
		service = new SettlementConfirmService(meetingAccessChecker, settlementGuard, inputAssembler,
				meetingPenaltyLoader, new SettlementConfirmGate(gameSessionRepository),
				new PenaltyChargeRecorder(penaltyChargeRepository), settlementRepository, revisionRepository, memberShareRepository,
				transferRepository, memberRepository, TestClocks.fixed(NOW));
		settlement = Settlement.createDraft(null);
		// 검사에서 먼저 실패하는 경우엔 벌칙을 읽지 않으므로 lenient
		lenient().when(meetingPenaltyLoader.load(MEETING_ID)).thenReturn(NO_PENALTIES);
	}

	@Test
	@DisplayName("확정하면 리비전 1과 멤버 스냅샷·송금 안내를 계산값대로 저장하고 CONFIRMED가 된다")
	void confirm() {
		givenLocked();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(NORMAL_INPUT);
		givenSaves();

		SettlementResponse response = service.confirm(MEETING_ID, HOST_ID, false);

		verify(revisionRepository).save(revisionCaptor.capture());
		SettlementRevision revision = revisionCaptor.getValue();
		assertThat(revision.getSettlement()).isSameAs(settlement);
		assertThat(revision.getRevisionNo()).isEqualTo(1);
		assertThat(revision.getConfirmedBy().getId()).isEqualTo(HOST_ID);
		assertThat(revision.getConfirmedAt()).isEqualTo(NOW);
		assertThat(revision.isWarningsAcknowledged()).isFalse();

		verify(memberShareRepository).saveAll(sharesCaptor.capture());
		assertThat(sharesCaptor.getValue()).allSatisfy(share -> assertThat(share.getRevision()).isSameAs(revision))
				.extracting(share -> share.getMember().getId(), RevisionMemberShare::getShareAmount,
						RevisionMemberShare::getNetBalance)
				.containsExactly(
						tuple(1L, 10_000L, 20_000L),
						tuple(2L, 10_000L, -10_000L),
						tuple(3L, 10_000L, -10_000L));

		verify(transferRepository).saveAll(transfersCaptor.capture());
		assertThat(transfersCaptor.getValue())
				.extracting(transfer -> transfer.getFromMember().getId(), transfer -> transfer.getToMember().getId(),
						Transfer::getAmount)
				.containsExactly(
						tuple(2L, 1L, 10_000L),
						tuple(3L, 1L, 10_000L));

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(settlement.getCurrentRevisionNo()).isEqualTo(1);
		assertThat(settlement.getFirstConfirmedAt()).isEqualTo(NOW);
		assertThat(response).isEqualTo(new SettlementResponse(SettlementStatus.CONFIRMED, 1, false,
				List.of(new SettlementMemberResponse(1L, 10_000L, 20_000L),
						new SettlementMemberResponse(2L, 10_000L, -10_000L),
						new SettlementMemberResponse(3L, 10_000L, -10_000L)),
				List.of(new SettlementTransferResponse(2L, 1L, 10_000L),
						new SettlementTransferResponse(3L, 1L, 10_000L)),
				List.of(), List.of()));
	}

	@Test
	@DisplayName("동시 확정 데드락을 막기 위해 정산 상태를 먼저 flush한 뒤 리비전을 저장한다")
	void flushesSettlementBeforeRevision() {
		givenLocked();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(NORMAL_INPUT);
		givenSaves();

		service.confirm(MEETING_ID, HOST_ID, false);

		InOrder order = inOrder(settlementGuard, settlementRepository, revisionRepository);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(settlementRepository).flush();
		order.verify(revisionRepository).save(any(SettlementRevision.class));
	}

	@Test
	@DisplayName("지출이 없어도 스냅샷 없는 리비전 1로 확정된다")
	void confirmWithoutExpenses() {
		givenLocked();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(new SettlementInput(List.of()));
		givenSaves();

		SettlementResponse response = service.confirm(MEETING_ID, HOST_ID, false);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(response.members()).isEmpty();
		assertThat(response.transfers()).isEmpty();
	}

	@Test
	@DisplayName("이미 CONFIRMED면 409 SETTLEMENT_ALREADY_CONFIRMED이고 계산·저장하지 않는다")
	void alreadyConfirmed() {
		ReflectionTestUtils.setField(settlement, "status", SettlementStatus.CONFIRMED);
		givenLocked();

		assertRejected(false, ErrorCode.SETTLEMENT_ALREADY_CONFIRMED);

		verifyNoInteractions(inputAssembler, revisionRepository);
	}

	@Test
	@DisplayName("REOPENED도 재확정 구현 전까지 409 SETTLEMENT_ALREADY_CONFIRMED다")
	void reopenedRejected() {
		ReflectionTestUtils.setField(settlement, "status", SettlementStatus.REOPENED);
		givenLocked();

		assertRejected(false, ErrorCode.SETTLEMENT_ALREADY_CONFIRMED);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REOPENED);
		verifyNoInteractions(revisionRepository);
	}

	@Test
	@DisplayName("호스트가 아니면 403이고 잠금·저장 없이 끝난다")
	void notHost() {
		given(meetingAccessChecker.requireHost(MEETING_ID, HOST_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		assertRejected(false, ErrorCode.NOT_MEETING_HOST);

		verifyNoInteractions(settlementGuard, revisionRepository);
	}

	@Test
	@DisplayName("경고를 확인하지 않으면 409 WARNING_UNCONFIRMED이고 DRAFT 그대로다")
	void warningUnconfirmed() {
		givenLocked();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(WARNING_INPUT);

		assertRejected(false, ErrorCode.WARNING_UNCONFIRMED);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(settlement.getCurrentRevisionNo()).isNull();
		verify(revisionRepository, never()).save(any());
	}

	@Test
	@DisplayName("경고를 확인하고 확정하면 warningsAcknowledged가 true다")
	void warningAcknowledged() {
		givenLocked();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(WARNING_INPUT);
		givenSaves();

		service.confirm(MEETING_ID, HOST_ID, true);

		verify(revisionRepository).save(revisionCaptor.capture());
		assertThat(revisionCaptor.getValue().isWarningsAcknowledged()).isTrue();
		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
	}

	@Test
	@DisplayName("경고가 없으면 요청에서 확인했다고 해도 warningsAcknowledged는 false다")
	void noWarningNotAcknowledged() {
		givenLocked();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(NORMAL_INPUT);
		givenSaves();

		service.confirm(MEETING_ID, HOST_ID, true);

		verify(revisionRepository).save(revisionCaptor.capture());
		assertThat(revisionCaptor.getValue().isWarningsAcknowledged()).isFalse();
	}

	@Test
	@DisplayName("조건 미설정 금액형 벌칙이 있으면 409 PENALTY_CONDITION_UNSET이고 DRAFT 그대로 아무것도 저장하지 않는다")
	void unsetPenaltyRejected() {
		GameSession game = PenaltyFixtures.revealedMoneyGame(70L, PenaltyFixtures.round(5L, 1, "고깃집"), NOW);
		MeetingPenalties penalties = new MeetingPenalties(List.of(PenaltyFixtures.penalty(100L, game, 2L)), Map.of());
		givenLocked();
		given(meetingPenaltyLoader.load(MEETING_ID)).willReturn(penalties);
		given(inputAssembler.assemble(MEETING_ID, penalties)).willReturn(NORMAL_INPUT);

		assertRejected(false, ErrorCode.PENALTY_CONDITION_UNSET);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.DRAFT);
		verifyNoInteractions(revisionRepository, penaltyChargeRepository);
	}

	@Test
	@DisplayName("대기 중인 금액형 게임이 있으면 409 MONEY_GAME_IN_PROGRESS이고 DRAFT 그대로다")
	void moneyGameInProgressRejected() {
		givenLocked();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(NORMAL_INPUT);
		GameSession waiting = GameSession.create(PenaltyFixtures.round(5L, 1, "고깃집"),
				GameType.LADDER, HOST_ID,
				PenaltyKind.MONEY, null, 1);
		given(gameSessionRepository.findAllMoneyInProgressByMeetingId(MEETING_ID)).willReturn(List.of(waiting));

		assertRejected(false, ErrorCode.MONEY_GAME_IN_PROGRESS);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.DRAFT);
		verifyNoInteractions(revisionRepository);
	}

	@Test
	@DisplayName("확정하면 APPLIED 벌칙별 부담액을 같은 리비전의 PenaltyCharge로 저장한다(예 1: RATIO 30% → 30,000원)")
	void confirmSnapshotsPenaltyCharge() {
		Round round = PenaltyFixtures.round(5L, 2, "볼링장");
		Expense bowling = PenaltyFixtures.expense(31L, round, 1L, 100_000L, "볼링");
		GameSession game = PenaltyFixtures.revealedMoneyGame(70L, round, NOW);
		Penalty penalty = PenaltyFixtures.penalty(100L, game, 2L);
		penalty.apply();
		PenaltyRule rule = PenaltyFixtures.ratioRule(90L, game, 30, bowling, HOST_ID, NOW);
		MeetingPenalties penalties = new MeetingPenalties(List.of(penalty), Map.of(70L, rule));
		givenLocked();
		given(meetingPenaltyLoader.load(MEETING_ID)).willReturn(penalties);
		given(inputAssembler.assemble(MEETING_ID, penalties)).willReturn(new SettlementInput(
				List.of(new ExpenseInput(31L, 100_000L, 1L, Set.of(1L, 2L, 3L, 4L), List.of())),
				List.of(new PenaltyRuleInput(31L, PenaltyMoneyType.RATIO, null, 30, NOW, 70L,
						List.of(new PenalizedMember(100L, 2L))))));
		givenSaves();
		given(penaltyChargeRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));

		service.confirm(MEETING_ID, HOST_ID, false);

		verify(revisionRepository).save(revisionCaptor.capture());
		verify(penaltyChargeRepository).saveAll(chargesCaptor.capture());
		assertThat(chargesCaptor.getValue()).singleElement().satisfies(charge -> {
			assertThat(charge.getRevision()).isSameAs(revisionCaptor.getValue());
			assertThat(charge.getPenalty()).isSameAs(penalty);
			assertThat(charge.getAmount()).isEqualTo(30_000L);
			assertThat(charge.getRatioPct()).isEqualTo(30);
			assertThat(charge.getTargetExpenseAmount()).isEqualTo(100_000L);
		});
	}

	private void assertRejected(boolean acknowledge, ErrorCode errorCode) {
		assertThatThrownBy(() -> service.confirm(MEETING_ID, HOST_ID, acknowledge))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
	}

	private void givenLocked() {
		given(settlementGuard.lockForAmountChange(MEETING_ID)).willReturn(settlement);
	}

	private void givenSaves() {
		given(memberRepository.getReferenceById(anyLong())).willAnswer(invocation -> member(invocation.getArgument(0)));
		given(revisionRepository.save(any(SettlementRevision.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(memberShareRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
		given(transferRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
	}

	private Member member(Long id) {
		Member member = Member.ofKakao(id, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
