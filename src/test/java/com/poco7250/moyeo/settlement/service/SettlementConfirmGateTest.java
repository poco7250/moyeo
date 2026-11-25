package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.settlement.calc.SettlementResult;
import com.poco7250.moyeo.settlement.calc.SettlementWarning;
import com.poco7250.moyeo.settlement.calc.SettlementWarningType;
import com.poco7250.moyeo.settlement.dto.InProgressGameResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import java.time.LocalDateTime;
import java.util.List;
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
			new SettlementWarning(SettlementWarningType.NO_SHARE_TARGET, 31L)));

	@Mock
	GameSessionRepository gameSessionRepository;

	private SettlementConfirmGate gate;
	private Round round;

	@BeforeEach
	void setUp() {
		gate = new SettlementConfirmGate(gameSessionRepository);
		round = PenaltyFixtures.round(5L, 2, "볼링장");
	}

	@Test
	@DisplayName("대기 중인 다시 돌린 금액형 회차가 있으면 409 MONEY_GAME_IN_PROGRESS와 게임 목록(원 회차 id 포함)을 싣는다")
	void moneyGameInProgress() {
		GameSession original = PenaltyFixtures.revealedMoneyGame(70L, round, REVEALED_AT);
		GameSession rerun = GameSession.rerunOf(original, 1L);
		ReflectionTestUtils.setField(rerun, "id", 72L);
		given(gameSessionRepository.findAllMoneyInProgressByMeetingId(MEETING_ID)).willReturn(List.of(rerun));

		assertThatThrownBy(() -> gate.check(MEETING_ID, WARNING, false))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MONEY_GAME_IN_PROGRESS);
					assertThat(e.getDetails()).isEqualTo(List.of(new InProgressGameResponse(72L, GameType.LADDER,
							GameStatus.WAITING, 10_000L, 70L)));
				});
	}

	@Test
	@DisplayName("경고가 있는데 확인하지 않으면 409 WARNING_UNCONFIRMED와 경고 내용을 싣는다")
	void warningUnconfirmed() {
		given(gameSessionRepository.findAllMoneyInProgressByMeetingId(MEETING_ID)).willReturn(List.of());

		assertThatThrownBy(() -> gate.check(MEETING_ID, WARNING, false))
				.isInstanceOfSatisfying(BusinessException.class, e -> {
					assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WARNING_UNCONFIRMED);
					assertThat(e.getDetails()).isEqualTo(List.of(
							new SettlementWarningResponse(SettlementWarningType.NO_SHARE_TARGET, 31L)));
				});
	}

	@Test
	@DisplayName("경고를 확인했거나 경고가 없으면 통과한다")
	void warningAcknowledgedOrNone() {
		given(gameSessionRepository.findAllMoneyInProgressByMeetingId(MEETING_ID)).willReturn(List.of());

		assertThatCode(() -> gate.check(MEETING_ID, WARNING, true))
				.doesNotThrowAnyException();
		assertThatCode(() -> gate.check(MEETING_ID, NO_WARNING, false))
				.doesNotThrowAnyException();
	}

	private static SettlementResult result(List<SettlementWarning> warnings) {
		return new SettlementResult(List.of(), List.of(), warnings, List.of());
	}
}
