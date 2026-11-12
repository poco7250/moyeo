package com.poco7250.moyeo.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameParticipant;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameRerunSnapshot;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameParticipantRepository;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.auth.GameAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameRerunServiceTest {

	private static final Long ROUND_ID = 100L;
	private static final Long ORIGINAL_ID = 300L;
	private static final Long RERUN_ID = 301L;
	private static final Long MEETING_ID = 50L;
	private static final Long CREATOR_ID = 1L;
	private static final Long HOST_ID = 2L;
	private static final LocalDateTime REVEALED_AT = LocalDateTime.of(2026, 12, 20, 21, 0);
	private static final GameStateResponse STATE = new GameStateResponse(RERUN_ID, ROUND_ID, GameType.LADDER,
			GameStatus.WAITING, HOST_ID, PenaltyKind.MONEY, "꼴찌가 2차 30%", 1, ORIGINAL_ID, ORIGINAL_ID, null,
			List.of(), null);

	@Mock
	GameAccessChecker gameAccessChecker;

	@Mock
	GameSessionRepository gameSessionRepository;

	@Mock
	GameParticipantRepository gameParticipantRepository;

	@Mock
	GameStateAssembler gameStateAssembler;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	@Mock
	ApplicationEventPublisher eventPublisher;

	@InjectMocks
	GameRerunService service;

	@Captor
	ArgumentCaptor<GameSession> rerunCaptor;

	@Captor
	ArgumentCaptor<List<GameParticipant>> participantsCaptor;

	Round round;

	@BeforeEach
	void setUp() {
		Meeting meeting = mock(Meeting.class);
		given(meeting.getId()).willReturn(MEETING_ID);
		round = mock(Round.class);
		given(round.getMeeting()).willReturn(meeting);
		given(gameStateAssembler.assemble(any())).willReturn(STATE);
		given(gameSessionRepository.saveAndFlush(any())).willAnswer(invocation -> {
			GameSession game = invocation.getArgument(0);
			ReflectionTestUtils.setField(game, "id", RERUN_ID);
			return game;
		});
	}

	@Test
	@DisplayName("호스트가 요청하면 원 회차를 복제한 WAITING 회차와 참가자가 생기고 모임 토픽으로 알린다")
	void requestCreatesRerun() {
		GameSession original = original(GameStatus.REVEALED, PenaltyKind.ACTION);
		givenOperator(original, HOST_ID);
		given(gameParticipantRepository.findAllByGameSessionIdOrderByIdAsc(ORIGINAL_ID))
				.willReturn(List.of(participant(original, 5L), participant(original, 6L)));

		GameStateResponse response = service.request(ORIGINAL_ID, HOST_ID);

		assertThat(response).isEqualTo(STATE);
		verify(gameSessionRepository).saveAndFlush(rerunCaptor.capture());
		GameSession rerun = rerunCaptor.getValue();
		assertThat(rerun.getStatus()).isEqualTo(GameStatus.WAITING);
		assertThat(rerun.getRerunOf()).isSameAs(original);
		assertThat(rerun.getCreatedBy()).isEqualTo(HOST_ID);
		assertThat(rerun.getType()).isEqualTo(original.getType());
		assertThat(rerun.getPenaltyKind()).isEqualTo(PenaltyKind.ACTION);
		assertThat(rerun.getPenaltyNote()).isEqualTo("꼴찌가 2차 30%");
		assertThat(rerun.getApplyOrderAt()).isEqualTo(REVEALED_AT);
		assertThat(rerun.getRootSessionId()).isEqualTo(ORIGINAL_ID);
		verify(gameParticipantRepository).saveAll(participantsCaptor.capture());
		assertThat(participantsCaptor.getValue()).extracting(participant -> participant.getMember().getId())
				.containsExactly(5L, 6L);
		assertThat(participantsCaptor.getValue()).allMatch(participant -> participant.getGameSession() == rerun);
		verify(eventPublisher).publishEvent(GameStateChangedEvent.toMeeting(MEETING_ID, STATE));
		// 원 회차 행은 수정하지 않는다
		assertThat(original.getStatus()).isEqualTo(GameStatus.REVEALED);
		verify(gameSessionRepository, never()).save(any());
	}

	@Test
	@DisplayName("원 회차 진행자도 종료된(CLOSED) 회차를 다시 돌릴 수 있다")
	void creatorCanRerunClosedGame() {
		givenOperator(original(GameStatus.CLOSED, PenaltyKind.NONE), CREATOR_ID);

		service.request(ORIGINAL_ID, CREATOR_ID);

		verify(gameSessionRepository).saveAndFlush(rerunCaptor.capture());
		assertThat(rerunCaptor.getValue().getCreatedBy()).isEqualTo(CREATOR_ID);
	}

	@Test
	@DisplayName("진행자·호스트가 아니면 403이고 아무것도 만들지 않는다")
	void operatorRequired() {
		willThrow(new BusinessException(ErrorCode.NOT_GAME_OPERATOR))
				.given(gameAccessChecker).requireOperator(ORIGINAL_ID, HOST_ID);

		assertRejected(ErrorCode.NOT_GAME_OPERATOR);
		verifyNothingCreated();
	}

	@ParameterizedTest
	@EnumSource(value = GameStatus.class, names = {"WAITING", "PLAYING", "CANCELED"})
	@DisplayName("공개되지 않았거나 취소된 회차는 다시 돌릴 수 없고 아무것도 만들지 않는다")
	void notRevealedRejected(GameStatus status) {
		givenOperator(original(status, PenaltyKind.MONEY), HOST_ID);

		assertRejected(ErrorCode.GAME_NOT_RERUNNABLE);
		verifyNothingCreated();
	}

	@Test
	@DisplayName("이미 후속 회차가 있으면 다시 돌릴 수 없고 아무것도 만들지 않는다")
	void existingRerunRejected() {
		givenOperator(original(GameStatus.REVEALED, PenaltyKind.MONEY), HOST_ID);
		given(gameSessionRepository.existsByRerunOfId(ORIGINAL_ID)).willReturn(true);

		assertRejected(ErrorCode.GAME_NOT_RERUNNABLE);
		verifyNothingCreated();
	}

	@Test
	@DisplayName("사전 검사를 같이 통과한 동시 요청은 유니크 위반으로 RERUN_ALREADY_EXISTS가 된다")
	void uniqueViolationBecomesRerunAlreadyExists() {
		givenOperator(original(GameStatus.REVEALED, PenaltyKind.MONEY), HOST_ID);
		willThrow(new DataIntegrityViolationException("duplicate")).given(gameSessionRepository).saveAndFlush(any());

		assertRejected(ErrorCode.RERUN_ALREADY_EXISTS);
		verifyNoInteractions(gameParticipantRepository, settlementGuard, changeLogRecorder, eventPublisher);
	}

	@Test
	@DisplayName("금액형 다시 돌리기는 정산에 반영한 뒤 새 회차 생성으로 변경 로그를 남긴다")
	void moneyRerunReflectsToSettlement() {
		givenOperator(original(GameStatus.REVEALED, PenaltyKind.MONEY), HOST_ID);

		service.request(ORIGINAL_ID, HOST_ID);

		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, HOST_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.GAME_SESSION, RERUN_ID, ChangeType.CREATE)));
		order.verify(changeLogRecorder).record(eq(MEETING_ID), eq(HOST_ID), eq(ChangeLogEntityType.GAME_SESSION),
				eq(RERUN_ID), eq(ChangeType.CREATE), eq(null),
				eq(new GameRerunSnapshot(GameStatus.WAITING, GameType.LADDER, PenaltyKind.MONEY, ORIGINAL_ID)));
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyKind.class, names = {"ACTION", "NONE"})
	@DisplayName("행동형·벌칙 없음 다시 돌리기는 정산과 변경 로그를 건드리지 않는다")
	void nonMoneyRerunLeavesSettlement(PenaltyKind penaltyKind) {
		givenOperator(original(GameStatus.REVEALED, penaltyKind), HOST_ID);

		service.request(ORIGINAL_ID, HOST_ID);

		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	private void givenOperator(GameSession original, Long memberId) {
		given(gameAccessChecker.requireOperator(ORIGINAL_ID, memberId)).willReturn(original);
	}

	private void assertRejected(ErrorCode expected) {
		assertThatThrownBy(() -> service.request(ORIGINAL_ID, HOST_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(expected);
	}

	private void verifyNothingCreated() {
		verify(gameSessionRepository, never()).saveAndFlush(any());
		verifyNoInteractions(gameParticipantRepository, settlementGuard, changeLogRecorder, eventPublisher);
	}

	private GameSession original(GameStatus status, PenaltyKind penaltyKind) {
		GameSession game = GameSession.create(round, GameType.LADDER, CREATOR_ID, penaltyKind, "꼴찌가 2차 30%", 1);
		ReflectionTestUtils.setField(game, "id", ORIGINAL_ID);
		game.markAsRoot();
		ReflectionTestUtils.setField(game, "status", status);
		ReflectionTestUtils.setField(game, "applyOrderAt", REVEALED_AT);
		return game;
	}

	private GameParticipant participant(GameSession game, Long memberId) {
		Member member = Member.ofKakao(memberId + 1000, "회원" + memberId, null, null);
		ReflectionTestUtils.setField(member, "id", memberId);
		return GameParticipant.create(game, member);
	}
}
