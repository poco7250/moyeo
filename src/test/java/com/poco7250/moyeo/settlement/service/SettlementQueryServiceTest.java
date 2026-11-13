package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.settlement.calc.PenalizedMember;
import com.poco7250.moyeo.settlement.calc.PenaltyRuleInput;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.settlement.calc.ExpenseInput;
import com.poco7250.moyeo.settlement.calc.SettlementInput;
import com.poco7250.moyeo.settlement.calc.SettlementWarningType;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.domain.Transfer;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import com.poco7250.moyeo.settlement.repository.TransferRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

	private static final Long MEETING_ID = 2L;
	private static final Long MEMBER_ID = 1L;
	private static final Long SETTLEMENT_ID = 40L;
	private static final Long REVISION_ID = 50L;
	private static final MeetingPenalties NO_PENALTIES = MeetingPenalties.empty();

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	SettlementRepository settlementRepository;

	@Mock
	SettlementInputAssembler inputAssembler;

	@Mock
	MeetingPenaltyLoader meetingPenaltyLoader;

	@Mock
	SettlementRevisionRepository revisionRepository;

	@Mock
	RevisionMemberShareRepository memberShareRepository;

	@Mock
	TransferRepository transferRepository;

	@InjectMocks
	SettlementQueryService service;

	private Settlement settlement;

	@BeforeEach
	void setUp() {
		settlement = Settlement.createDraft(null);
		ReflectionTestUtils.setField(settlement, "id", SETTLEMENT_ID);
		// 인가·정산 조회에서 먼저 실패하는 경우엔 계산까지 가지 않으므로 lenient
		lenient().when(meetingPenaltyLoader.load(MEETING_ID)).thenReturn(NO_PENALTIES);
		lenient().when(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).thenReturn(new SettlementInput(List.of()));
	}

	@Test
	@DisplayName("DRAFT면 현재 계산값을 기준 리비전 없는 미리보기로 돌려주고 리비전을 읽지 않는다")
	void draftPreview() {
		givenSettlement();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(new SettlementInput(List.of(
				new ExpenseInput(31L, 20_000L, 1L, Set.of(1L, 2L), List.of()))));

		SettlementResponse response = service.get(MEETING_ID, MEMBER_ID);

		assertThat(response).isEqualTo(new SettlementResponse(SettlementStatus.DRAFT, null, true,
				List.of(new SettlementMemberResponse(1L, 10_000L, 10_000L),
						new SettlementMemberResponse(2L, 10_000L, -10_000L)),
				List.of(new SettlementTransferResponse(2L, 1L, 10_000L)),
				List.of(), List.of(), null));
		verifyNoInteractions(revisionRepository, memberShareRepository, transferRepository);
	}

	@Test
	@DisplayName("DRAFT 미리보기에는 계산 경고가 담긴다")
	void draftWarnings() {
		givenSettlement();
		given(inputAssembler.assemble(MEETING_ID, NO_PENALTIES)).willReturn(new SettlementInput(List.of(
				new ExpenseInput(31L, 20_000L, 1L, Set.of(), List.of()))));

		assertThat(service.get(MEETING_ID, MEMBER_ID).warnings())
				.containsExactly(new SettlementWarningResponse(SettlementWarningType.NO_SHARE_TARGET, 31L));
	}

	@ParameterizedTest
	@EnumSource(value = SettlementStatus.class, names = {"CONFIRMED", "REOPENED"})
	@DisplayName("확정 이후에는 마지막 확정 리비전의 저장값을 돌려준다")
	void savedRevision(SettlementStatus status) {
		ReflectionTestUtils.setField(settlement, "status", status);
		ReflectionTestUtils.setField(settlement, "currentRevisionNo", 1);
		givenSettlement();
		SettlementRevision revision = SettlementRevision.create(settlement, 1, member(1L),
				LocalDateTime.of(2026, 12, 20, 22, 0), false);
		ReflectionTestUtils.setField(revision, "id", REVISION_ID);
		given(revisionRepository.findBySettlementIdAndRevisionNo(SETTLEMENT_ID, 1)).willReturn(Optional.of(revision));
		given(memberShareRepository.findAllByRevisionIdOrderByMemberIdAsc(REVISION_ID)).willReturn(List.of(
				RevisionMemberShare.create(revision, member(1L), 10_000L, 10_000L),
				RevisionMemberShare.create(revision, member(2L), 10_000L, -10_000L)));
		given(transferRepository.findAllByRevisionIdOrderByIdAsc(REVISION_ID)).willReturn(List.of(
				Transfer.create(revision, member(2L), member(1L), 10_000L)));

		SettlementResponse response = service.get(MEETING_ID, MEMBER_ID);

		assertThat(response).isEqualTo(new SettlementResponse(status, 1, false,
				List.of(new SettlementMemberResponse(1L, 10_000L, 10_000L),
						new SettlementMemberResponse(2L, 10_000L, -10_000L)),
				List.of(new SettlementTransferResponse(2L, 1L, 10_000L)),
				List.of(), List.of(), null));
	}

	@Test
	@DisplayName("벌칙 반영 내역: APPLIED는 출처·대상 지출·부담액·설정자·시각, ASSIGNED는 금액 없음, WAIVED는 면제자·시각")
	void penaltyItemsByStatus() {
		givenSettlement();
		PenaltyScenario scenario = new PenaltyScenario();
		given(meetingPenaltyLoader.load(MEETING_ID)).willReturn(scenario.penalties);
		given(inputAssembler.assemble(MEETING_ID, scenario.penalties)).willReturn(scenario.input());

		List<SettlementPenaltyResponse> items = service.get(MEETING_ID, MEMBER_ID).penalties();

		assertThat(items).containsExactly(scenario.appliedItem(), scenario.assignedItem(), scenario.waivedItem());
	}

	@ParameterizedTest
	@EnumSource(value = SettlementStatus.class, names = {"CONFIRMED", "REOPENED"})
	@DisplayName("확정 이후에도 벌칙 반영 내역은 현재 벌칙 상태와 현재 계산 부담액이다")
	void penaltyItemsAfterConfirm(SettlementStatus status) {
		ReflectionTestUtils.setField(settlement, "status", status);
		ReflectionTestUtils.setField(settlement, "currentRevisionNo", 1);
		givenSettlement();
		SettlementRevision revision = SettlementRevision.create(settlement, 1, member(1L),
				LocalDateTime.of(2026, 12, 20, 22, 0), false);
		ReflectionTestUtils.setField(revision, "id", REVISION_ID);
		given(revisionRepository.findBySettlementIdAndRevisionNo(SETTLEMENT_ID, 1)).willReturn(Optional.of(revision));
		PenaltyScenario scenario = new PenaltyScenario();
		given(meetingPenaltyLoader.load(MEETING_ID)).willReturn(scenario.penalties);
		given(inputAssembler.assemble(MEETING_ID, scenario.penalties)).willReturn(scenario.input());

		SettlementResponse response = service.get(MEETING_ID, MEMBER_ID);

		assertThat(response.preview()).isFalse();
		assertThat(response.penalties())
				.containsExactly(scenario.appliedItem(), scenario.assignedItem(), scenario.waivedItem());
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403이고 정산을 읽지 않는다")
	void notMember() {
		given(meetingAccessChecker.requireMember(MEETING_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertThatThrownBy(() -> service.get(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(settlementRepository);
	}

	@Test
	@DisplayName("정산 행이 없으면 MEETING_NOT_FOUND다")
	void settlementMissing() {
		given(settlementRepository.findByMeetingId(MEETING_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETING_NOT_FOUND);
	}

	@Test
	@DisplayName("확정 상태인데 리비전이 없으면 불변식 위반이다")
	void revisionMissing() {
		ReflectionTestUtils.setField(settlement, "status", SettlementStatus.CONFIRMED);
		ReflectionTestUtils.setField(settlement, "currentRevisionNo", 1);
		givenSettlement();
		given(revisionRepository.findBySettlementIdAndRevisionNo(SETTLEMENT_ID, 1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(MEETING_ID, MEMBER_ID)).isInstanceOf(IllegalStateException.class);
	}

	private void givenSettlement() {
		given(settlementRepository.findByMeetingId(MEETING_ID)).willReturn(Optional.of(settlement));
	}

	private Member member(Long id) {
		Member member = Member.ofKakao(id, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	/**
	 * 예 1 기반: 2차 "볼링" 100,000원(결제 철수=1, 참석 1~4). 사다리 3판 — 민수(2) RATIO 30% APPLIED,
	 * 영희(3) ASSIGNED, 지수(4) WAIVED.
	 */
	private static final class PenaltyScenario {

		static final LocalDateTime SET_AT = LocalDateTime.of(2026, 12, 20, 21, 30);
		static final LocalDateTime WAIVED_AT = LocalDateTime.of(2026, 12, 20, 21, 40);

		final Round round = PenaltyFixtures.round(5L, 2, "볼링장");
		final Expense bowling = PenaltyFixtures.expense(31L, round, 1L, 100_000L, "볼링");
		final GameSession appliedGame = PenaltyFixtures.revealedMoneyGame(70L, round,
				LocalDateTime.of(2026, 12, 20, 21, 0));
		final GameSession assignedGame = PenaltyFixtures.revealedMoneyGame(71L, round,
				LocalDateTime.of(2026, 12, 20, 21, 5));
		final GameSession waivedGame = PenaltyFixtures.revealedMoneyGame(72L, round,
				LocalDateTime.of(2026, 12, 20, 21, 10));
		final Penalty applied = PenaltyFixtures.penalty(80L, appliedGame, 2L);
		final Penalty assigned = PenaltyFixtures.penalty(81L, assignedGame, 3L);
		final Penalty waived = PenaltyFixtures.penalty(82L, waivedGame, 4L);
		final PenaltyRule rule = PenaltyFixtures.ratioRule(90L, appliedGame, 30, bowling, 1L, SET_AT);
		final MeetingPenalties penalties;

		PenaltyScenario() {
			applied.apply();
			waived.waive(1L, WAIVED_AT);
			penalties = new MeetingPenalties(List.of(applied, assigned, waived), Map.of(70L, rule));
		}

		SettlementInput input() {
			return new SettlementInput(
					List.of(new ExpenseInput(31L, 100_000L, 1L, Set.of(1L, 2L, 3L, 4L), List.of())),
					List.of(new PenaltyRuleInput(31L, PenaltyMoneyType.RATIO, null, 30,
							appliedGame.getApplyOrderAt(), 70L, List.of(new PenalizedMember(80L, 2L)))));
		}

		SettlementPenaltyResponse appliedItem() {
			return new SettlementPenaltyResponse(80L, 2L, PenaltyStatus.APPLIED,
					new SettlementPenaltyResponse.Game(70L, GameType.LADDER, 2, "볼링장"),
					new SettlementPenaltyResponse.Rule(PenaltyMoneyType.RATIO, null, 30,
							new SettlementPenaltyResponse.TargetExpense(31L, 2, "볼링장", "볼링", 100_000L),
							1L, SET_AT),
					30_000L, null, null);
		}

		SettlementPenaltyResponse assignedItem() {
			return new SettlementPenaltyResponse(81L, 3L, PenaltyStatus.ASSIGNED,
					new SettlementPenaltyResponse.Game(71L, GameType.LADDER, 2, "볼링장"), null, null, null, null);
		}

		SettlementPenaltyResponse waivedItem() {
			return new SettlementPenaltyResponse(82L, 4L, PenaltyStatus.WAIVED,
					new SettlementPenaltyResponse.Game(72L, GameType.LADDER, 2, "볼링장"), null, null, 1L, WAIVED_AT);
		}
	}
}
