package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.calc.SettlementResult;
import com.poco7250.moyeo.settlement.calc.SettlementWarning;
import com.poco7250.moyeo.settlement.calc.SettlementWarningType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.dto.InProgressGameResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import com.poco7250.moyeo.settlement.dto.UnsetPenaltyGameResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementConfirmGateTest {

	private static final Long MEETING_ID = 2L;
	private static final LocalDateTime REVEALED_AT = LocalDateTime.of(2026, 12, 20, 21, 0);
	private static final SettlementResult NO_WARNING = result(List.of());
	private static final SettlementResult WARNING = result(List.of(
			new SettlementWarning(SettlementWarningType.PENALTY_EXCEEDS_TARGET, 31L)));

	@Mock
	GameSessionRepository gameSessionRepository;

	private SettlementConfirmGate gate;
	private Round round;
	private Expense bowling;

	@BeforeEach
	void setUp() {
		gate = new SettlementConfirmGate(gameSessionRepository);
		round = PenaltyFixtures.round(5L, 2, "볼링장");
		bowling = PenaltyFixtures.expense(31L, round, 1L, 100_000L, "볼링");
	}

	@Test
	@DisplayName("금액형 ASSIGNED 벌칙이 있으면 409 PENALTY_CONDITION_UNSET과 게임별 미설정 목록을 싣고 게임은 조회하지 않는다")
	void unsetPenalty() {
		GameSession unsetGame = PenaltyFixtures.revealedMoneyGame(70L, round, REVEALED_AT);
		GameSession appliedGame = PenaltyFixtures.revealedMoneyGame(71L, round, REVEALED_AT.plusMinutes(1));
		Penalty minsu = PenaltyFixtures.penalty(100L, unsetGame, 2L);
		Penalty younghee = PenaltyFixtures.penalty(101L, unsetGame, 3L);
		Penalty applied = PenaltyFixtures.penalty(102L, appliedGame, 4L);
		applied.apply();
		PenaltyRule rule = PenaltyFixtures.ratioRule(90L, appliedGame, 30, bowling, 1L, REVEALED_AT);
		MeetingPenalties penalties = new MeetingPenalties(List.of(minsu, younghee, applied), Map.of(71L, rule));

		assertThatThrownBy(() -> gate.check(MEETING_ID, penalties, WARNING, false))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PENALTY_CONDITION_UNSET);
					assertThat(e.getDetails()).isEqualTo(List.of(new UnsetPenaltyGameResponse(70L, GameType.LADDER,
							2, "볼링장", List.of(100L, 101L), List.of(2L, 3L))));
				});
		verifyNoInteractions(gameSessionRepository);
	}

	@Test
	@DisplayName("APPLIED·WAIVED 벌칙만 있으면 미설정 검사를 통과한다(행동형은 입력에 없다)")
	void appliedAndWaivedPass() {
		GameSession appliedGame = PenaltyFixtures.revealedMoneyGame(70L, round, REVEALED_AT);
		GameSession waivedGame = PenaltyFixtures.revealedMoneyGame(71L, round, REVEALED_AT.plusMinutes(1));
		Penalty applied = PenaltyFixtures.penalty(100L, appliedGame, 2L);
		applied.apply();
		Penalty waived = PenaltyFixtures.penalty(101L, waivedGame, 3L);
		waived.waive(1L, REVEALED_AT);
		PenaltyRule rule = PenaltyFixtures.ratioRule(90L, appliedGame, 30, bowling, 1L, REVEALED_AT);
		MeetingPenalties penalties = new MeetingPenalties(List.of(applied, waived), Map.of(70L, rule));
		given(gameSessionRepository.findAllMoneyInProgressByMeetingId(MEETING_ID)).willReturn(List.of());

		assertThatCode(() -> gate.check(MEETING_ID, penalties, NO_WARNING, false)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("대기 중인 다시 돌린 금액형 회차가 있으면 409 MONEY_GAME_IN_PROGRESS와 게임 목록(원 회차 id 포함)을 싣는다")
	void moneyGameInProgress() {
		GameSession original = PenaltyFixtures.revealedMoneyGame(70L, round, REVEALED_AT);
		GameSession rerun = GameSession.rerunOf(original, 1L);
		ReflectionTestUtils.setField(rerun, "id", 72L);
		given(gameSessionRepository.findAllMoneyInProgressByMeetingId(MEETING_ID)).willReturn(List.of(rerun));

		assertThatThrownBy(() -> gate.check(MEETING_ID, MeetingPenalties.empty(), WARNING, false))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MONEY_GAME_IN_PROGRESS);
					assertThat(e.getDetails()).isEqualTo(List.of(new InProgressGameResponse(72L, GameType.LADDER,
							GameStatus.WAITING, 2, "볼링장", 70L)));
				});
	}

	@Test
	@DisplayName("경고가 있는데 확인하지 않으면 409 WARNING_UNCONFIRMED와 경고 내용을 싣는다")
	void warningUnconfirmed() {
		given(gameSessionRepository.findAllMoneyInProgressByMeetingId(MEETING_ID)).willReturn(List.of());

		assertThatThrownBy(() -> gate.check(MEETING_ID, MeetingPenalties.empty(), WARNING, false))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WARNING_UNCONFIRMED);
					assertThat(e.getDetails()).isEqualTo(List.of(
							new SettlementWarningResponse(SettlementWarningType.PENALTY_EXCEEDS_TARGET, 31L)));
				});
	}

	@Test
	@DisplayName("경고를 확인했거나 경고가 없으면 통과한다")
	void warningAcknowledgedOrNone() {
		given(gameSessionRepository.findAllMoneyInProgressByMeetingId(MEETING_ID)).willReturn(List.of());

		assertThatCode(() -> gate.check(MEETING_ID, MeetingPenalties.empty(), WARNING, true))
				.doesNotThrowAnyException();
		assertThatCode(() -> gate.check(MEETING_ID, MeetingPenalties.empty(), NO_WARNING, false))
				.doesNotThrowAnyException();
	}

	private static SettlementResult result(List<SettlementWarning> warnings) {
		return new SettlementResult(List.of(), List.of(), warnings, List.of());
	}
}
