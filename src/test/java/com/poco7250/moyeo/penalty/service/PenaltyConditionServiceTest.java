package com.poco7250.moyeo.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyWaiverSnapshot;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
	private static final long AMOUNT = 10_000L;
	private static final Long HOST_ID = 4L;
	private static final Long MINSU_ID = 2L;
	private static final Long YEONGHEE_ID = 3L;
	private static final List<Long> PENALTY_MEMBER_IDS = List.of(MINSU_ID, YEONGHEE_ID);
	private static final LocalDateTime REVEALED_AT = LocalDateTime.of(2026, 12, 20, 21, 0);
	private static final LocalDateTime OLD_WAIVED_AT = LocalDateTime.of(2026, 12, 20, 21, 40);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 22, 0);

	@Mock
	GameAccessChecker gameAccessChecker;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	PenaltyRepository penaltyRepository;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	PenaltyConditionService service;

	GameSession game;
	List<Penalty> penalties;

	@BeforeEach
	void setUp() {
		service = new PenaltyConditionService(
				new PenaltyConditionLoader(gameAccessChecker, settlementGuard, penaltyRepository),
				settlementGuard, changeLogRecorder, TestClocks.fixed(NOW));
		game = revealedGame(PenaltyKind.MONEY);
		penalties = List.of(penalty(11L, MINSU_ID), penalty(12L, YEONGHEE_ID));
		given(gameAccessChecker.requireSettlementManager(GAME_ID, HOST_ID)).willAnswer(invocation -> game);
		given(penaltyRepository.findAllByGameSessionIdAndStatusNot(GAME_ID, PenaltyStatus.VOIDED))
				.willAnswer(invocation -> penalties);
	}

	@Test
	@DisplayName("금액형 게임 벌칙은 공개되면 APPLIED이고, 면제하면 전원 WAIVED가 되며 면제자·시각과 전/후 값이 남는다")
	void waive() {
		assertThat(penalties).extracting(Penalty::getStatus).containsOnly(PenaltyStatus.APPLIED);

		PenaltyConditionResponse response = service.waive(GAME_ID, HOST_ID);

		assertThat(penalties).allSatisfy(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.WAIVED);
			assertThat(penalty.getWaivedBy()).isEqualTo(HOST_ID);
			assertThat(penalty.getWaivedAt()).isEqualTo(NOW);
		});
		assertThat(response.status()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(response.penaltyAmount()).isEqualTo(AMOUNT);
		assertThat(response.waivedBy()).isEqualTo(HOST_ID);
		verify(settlementGuard).lockForAmountChange(MEETING_ID);
		verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.PENALTY, GAME_ID, ChangeType.UPDATE)));
		verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY, GAME_ID,
				ChangeType.UPDATE, notWaivedSnapshot(), waivedSnapshot(NOW));
	}

	@Test
	@DisplayName("이미 WAIVED인 게임을 다시 면제하면 409다")
	void waiveOnWaived() {
		givenWaived();

		assertRejected(() -> service.waive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("면제를 취소하면 전원 APPLIED로 돌아가 다시 정산에 반영되고 면제 정보가 비워진다")
	void unwaive() {
		givenWaived();

		PenaltyConditionResponse response = service.unwaive(GAME_ID, HOST_ID);

		assertThat(penalties).allSatisfy(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.APPLIED);
			assertThat(penalty.getWaivedBy()).isNull();
			assertThat(penalty.getWaivedAt()).isNull();
		});
		assertThat(response.status()).isEqualTo(PenaltyStatus.APPLIED);
		verify(changeLogRecorder).record(MEETING_ID, HOST_ID, ChangeLogEntityType.PENALTY, GAME_ID,
				ChangeType.UPDATE, waivedSnapshot(OLD_WAIVED_AT), notWaivedSnapshot());
	}

	@Test
	@DisplayName("면제 상태가 아닌 게임의 면제 취소는 409다")
	void unwaiveOnApplied() {
		assertRejected(() -> service.unwaive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("정산 권한이 없으면 403 NOT_MEETING_HOST이고 정산을 잠그지 않는다")
	void notHost() {
		given(gameAccessChecker.requireSettlementManager(GAME_ID, MINSU_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		assertRejected(() -> service.waive(GAME_ID, MINSU_ID), ErrorCode.NOT_MEETING_HOST);
		assertRejected(() -> service.unwaive(GAME_ID, MINSU_ID), ErrorCode.NOT_MEETING_HOST);

		verifyNoInteractions(settlementGuard, penaltyRepository, changeLogRecorder);
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyKind.class, names = {"NONE", "ACTION"})
	@DisplayName("금액형이 아닌 게임은 409 PENALTY_NOT_CONFIGURABLE이다")
	void notMoneyGame(PenaltyKind penaltyKind) {
		game = revealedGame(penaltyKind);

		assertRejected(() -> service.waive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("금액 없는 옛 금액형 게임은 409 PENALTY_NOT_CONFIGURABLE이다")
	void moneyGameWithoutAmount() {
		ReflectionTestUtils.setField(game, "penaltyAmount", null);

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
			assertRejected(() -> service.waive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);
		}

		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("다시 돌리기로 벌칙이 전부 VOIDED면 409 PENALTY_NOT_CONFIGURABLE이다")
	void voidedPenalties() {
		penalties = List.of();

		assertRejected(() -> service.waive(GAME_ID, HOST_ID), ErrorCode.PENALTY_NOT_CONFIGURABLE);

		verify(settlementGuard, never()).markAmountChanged(any(), any(), any());
		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("CLOSED 게임도 REVEALED와 똑같이 면제할 수 있다")
	void closedGame() {
		game.close();

		PenaltyConditionResponse response = service.waive(GAME_ID, HOST_ID);

		assertThat(response.status()).isEqualTo(PenaltyStatus.WAIVED);
	}

	private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
		assertThatThrownBy(action)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(expected);
	}

	private void givenWaived() {
		penalties.forEach(penalty -> penalty.waive(HOST_ID, OLD_WAIVED_AT));
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
		GameSession created = GameSession.create(meeting, GameType.LADDER, MINSU_ID, penaltyKind,
				penaltyKind == PenaltyKind.MONEY ? AMOUNT : null, null, 2);
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
