package com.poco7250.moyeo.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameParticipant;
import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameParticipantRepository;
import com.poco7250.moyeo.game.repository.GameResultRepository;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.auth.GameAccessChecker;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.service.PenaltyAssigner;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameLobbyServiceTest {

	private static final Long ROUND_ID = 100L;
	private static final Long GAME_ID = 300L;
	private static final Long MEETING_ID = 50L;
	private static final Long ACTOR_ID = 1L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 21, 0);
	private static final GameStateResponse STATE = new GameStateResponse(GAME_ID, ROUND_ID, GameType.LADDER,
			GameStatus.WAITING, ACTOR_ID, PenaltyKind.MONEY, "꼴찌가 2차 30%", 1, GAME_ID, null, null, List.of(), null);

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	GameAccessChecker gameAccessChecker;

	@Mock
	GameSessionRepository gameSessionRepository;

	@Mock
	GameParticipantRepository gameParticipantRepository;

	@Mock
	GameResultRepository gameResultRepository;

	@Mock
	AttendanceRepository attendanceRepository;

	@Mock
	GameStateAssembler gameStateAssembler;

	@Mock
	GameSeedGenerator gameSeedGenerator;

	@Mock
	PenaltyAssigner penaltyAssigner;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	@Mock
	ApplicationEventPublisher eventPublisher;

	@Captor
	ArgumentCaptor<Iterable<GameResult>> resultsCaptor;

	@Captor
	ArgumentCaptor<List<Member>> penalizedCaptor;

	GameLobbyService service;
	Round round;

	@BeforeEach
	void setUp() {
		service = new GameLobbyService(meetingAccessChecker, gameAccessChecker, gameSessionRepository,
				gameParticipantRepository, gameResultRepository, attendanceRepository, gameStateAssembler,
				gameSeedGenerator, penaltyAssigner, settlementGuard, changeLogRecorder, eventPublisher, TestClocks.fixed(NOW));
		Meeting meeting = mock(Meeting.class);
		given(meeting.getId()).willReturn(MEETING_ID);
		round = mock(Round.class);
		given(round.getMeeting()).willReturn(meeting);
		given(gameStateAssembler.assemble(any())).willReturn(STATE);
	}

	@Test
	@DisplayName("고른 참석자로 게임을 만들고 만든 회원이 진행자, 자기 id가 원 회차가 된다")
	void createSavesSelectedParticipants() {
		givenRoundAccess();
		givenAttending(List.of(2L, 3L), member(2L), member(3L));
		given(gameSessionRepository.save(any())).willAnswer(invocation -> {
			GameSession game = invocation.getArgument(0);
			ReflectionTestUtils.setField(game, "id", GAME_ID);
			return game;
		});

		service.create(ROUND_ID, ACTOR_ID, request(GameType.LADDER, List.of(2L, 3L)));

		ArgumentCaptor<GameSession> gameCaptor = ArgumentCaptor.forClass(GameSession.class);
		verify(gameSessionRepository).save(gameCaptor.capture());
		GameSession saved = gameCaptor.getValue();
		assertThat(saved.getCreatedBy()).isEqualTo(ACTOR_ID);
		assertThat(saved.getRootSessionId()).isEqualTo(GAME_ID);
		assertThat(saved.getPenaltyKind()).isEqualTo(PenaltyKind.MONEY);
		assertThat(saved.getPenaltyNote()).isEqualTo("꼴찌가 2차 30%");
		assertThat(saved.getPenaltyCount()).isEqualTo(1);
		ArgumentCaptor<Iterable<GameParticipant>> participants = participantCaptor();
		verify(gameParticipantRepository).saveAll(participants.capture());
		assertThat(participants.getValue()).hasSize(2);
		verify(eventPublisher).publishEvent(GameStateChangedEvent.toMeeting(MEETING_ID, STATE));
	}

	@Test
	@DisplayName("차수 참석자가 아닌 회원이 섞이면 게임을 만들지 않는다")
	void createRejectsNonAttending() {
		givenRoundAccess();
		givenAttending(List.of(2L, 3L), member(2L));

		assertRejected(() -> service.create(ROUND_ID, ACTOR_ID, request(GameType.LADDER, List.of(2L, 3L))),
				ErrorCode.GAME_PARTICIPANT_NOT_ATTENDING);
		verify(gameSessionRepository, never()).save(any());
	}

	@Test
	@DisplayName("룰렛은 아직 만들 수 없다")
	void createRejectsRoulette() {
		givenRoundAccess();

		assertRejected(() -> service.create(ROUND_ID, ACTOR_ID, request(GameType.ROULETTE, List.of(2L, 3L))),
				ErrorCode.GAME_TYPE_NOT_SUPPORTED);
		verifyNoInteractions(gameSessionRepository);
	}

	@Test
	@DisplayName("시작하면 벌칙자를 뽑아 전원의 결과를 저장하고 공개하며 뽑힌 회원으로 벌칙자를 확정한다")
	void startDrawsAndReveals() {
		GameSession game = gameWith(GameStatus.WAITING, PenaltyKind.MONEY);
		givenOperator(game);
		given(gameParticipantRepository.findAllByGameSessionIdOrderByIdAsc(GAME_ID))
				.willReturn(List.of(participant(game, 1L), participant(game, 2L), participant(game, 3L)));
		given(gameSeedGenerator.next()).willReturn(42L);

		service.start(GAME_ID, ACTOR_ID);

		assertThat(game.getStatus()).isEqualTo(GameStatus.REVEALED);
		assertThat(game.getSeed()).isEqualTo(42L);
		assertThat(game.getRevealedAt()).isEqualTo(NOW);
		assertThat(game.getApplyOrderAt()).isEqualTo(NOW);
		verify(gameResultRepository).saveAll(resultsCaptor.capture());
		assertThat(resultsCaptor.getValue()).hasSize(3)
				.filteredOn(result -> GameResult.OUTCOME_PENALTY.equals(result.getOutcome())).hasSize(1);
		verify(penaltyAssigner).onRevealed(eq(game), eq(ACTOR_ID), penalizedCaptor.capture());
		assertThat(penalizedCaptor.getValue()).extracting(Member::getId).containsExactly(penaltyMemberId());
		verifyPublishedToBothTopics();
		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("진행자·호스트가 아니면 시작·종료·취소할 수 없다")
	void operatorRequired() {
		willThrow(new BusinessException(ErrorCode.NOT_GAME_OPERATOR))
				.given(gameAccessChecker).requireOperator(GAME_ID, ACTOR_ID);

		assertRejected(() -> service.start(GAME_ID, ACTOR_ID), ErrorCode.NOT_GAME_OPERATOR);
		assertRejected(() -> service.close(GAME_ID, ACTOR_ID), ErrorCode.NOT_GAME_OPERATOR);
		assertRejected(() -> service.cancel(GAME_ID, ACTOR_ID), ErrorCode.NOT_GAME_OPERATOR);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("이미 공개된 게임은 다시 시작할 수 없고 추첨도 없다")
	void startRejectedAfterReveal() {
		GameSession game = gameWith(GameStatus.REVEALED, PenaltyKind.MONEY);
		givenOperator(game);

		assertRejected(() -> service.start(GAME_ID, ACTOR_ID), ErrorCode.GAME_INVALID_TRANSITION);
		verifyNoInteractions(gameResultRepository, gameSeedGenerator, penaltyAssigner, eventPublisher);
	}

	@Test
	@DisplayName("공개된 게임을 종료해도 정산은 건드리지 않는다")
	void closeLeavesSettlement() {
		GameSession game = gameWith(GameStatus.REVEALED, PenaltyKind.MONEY);
		givenOperator(game);

		service.close(GAME_ID, ACTOR_ID);

		assertThat(game.getStatus()).isEqualTo(GameStatus.CLOSED);
		verifyPublishedToBothTopics();
		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("공개 전 게임은 종료할 수 없다")
	void closeRejectedBeforeReveal() {
		givenOperator(gameWith(GameStatus.WAITING, PenaltyKind.NONE));

		assertRejected(() -> service.close(GAME_ID, ACTOR_ID), ErrorCode.GAME_INVALID_TRANSITION);
	}

	@Test
	@DisplayName("금액형 게임 취소는 정산을 직렬화하고 변경 로그를 남긴다")
	void cancelMoneyGame() {
		GameSession game = gameWith(GameStatus.WAITING, PenaltyKind.MONEY);
		givenOperator(game);

		service.cancel(GAME_ID, ACTOR_ID);

		assertThat(game.getStatus()).isEqualTo(GameStatus.CANCELED);
		verify(settlementGuard).lockOnly(MEETING_ID);
		verify(changeLogRecorder).record(eq(MEETING_ID), eq(ACTOR_ID), eq(ChangeLogEntityType.GAME_SESSION), eq(GAME_ID),
				eq(ChangeType.UPDATE), any(), any());
		verifyPublishedToBothTopics();
	}

	@Test
	@DisplayName("행동형 게임 취소는 정산과 변경 로그를 건드리지 않는다")
	void cancelActionGame() {
		givenOperator(gameWith(GameStatus.WAITING, PenaltyKind.ACTION));

		service.cancel(GAME_ID, ACTOR_ID);

		verify(settlementGuard, never()).lockOnly(anyLong());
		verifyNoInteractions(changeLogRecorder);
	}

	@Test
	@DisplayName("공개된 게임은 취소할 수 없다")
	void cancelRejectedAfterReveal() {
		givenOperator(gameWith(GameStatus.REVEALED, PenaltyKind.MONEY));

		assertRejected(() -> service.cancel(GAME_ID, ACTOR_ID), ErrorCode.GAME_NOT_CANCELABLE);
		verifyNoInteractions(settlementGuard, eventPublisher);
	}

	private Long penaltyMemberId() {
		for (GameResult result : resultsCaptor.getValue()) {
			if (GameResult.OUTCOME_PENALTY.equals(result.getOutcome())) {
				return result.getMember().getId();
			}
		}
		throw new AssertionError("벌칙자 결과가 없습니다.");
	}

	private void givenRoundAccess() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, ACTOR_ID)).willReturn(round);
	}

	private void givenAttending(List<Long> memberIds, Member... members) {
		given(attendanceRepository.findAttendingMembers(ROUND_ID, memberIds)).willReturn(List.of(members));
	}

	private void givenOperator(GameSession game) {
		given(gameAccessChecker.requireOperator(GAME_ID, ACTOR_ID)).willReturn(game);
	}

	private void verifyPublishedToBothTopics() {
		verify(eventPublisher).publishEvent(GameStateChangedEvent.toGame(GAME_ID, STATE));
		verify(eventPublisher).publishEvent(GameStateChangedEvent.toMeeting(MEETING_ID, STATE));
	}

	private GameCreateRequest request(GameType type, List<Long> memberIds) {
		return new GameCreateRequest(type, PenaltyKind.MONEY, "꼴찌가 2차 30%", memberIds, null);
	}

	private GameSession gameWith(GameStatus status, PenaltyKind penaltyKind) {
		GameSession game = GameSession.create(round, GameType.LADDER, ACTOR_ID, penaltyKind, null, 1);
		ReflectionTestUtils.setField(game, "id", GAME_ID);
		ReflectionTestUtils.setField(game, "status", status);
		return game;
	}

	private GameParticipant participant(GameSession game, Long memberId) {
		return GameParticipant.create(game, member(memberId));
	}

	private Member member(Long id) {
		Member member = Member.ofKakao(id + 1000, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	@SuppressWarnings("unchecked")
	private ArgumentCaptor<Iterable<GameParticipant>> participantCaptor() {
		return ArgumentCaptor.forClass((Class<Iterable<GameParticipant>>) (Class<?>) Iterable.class);
	}

	private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
		assertThatThrownBy(action)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(expected);
	}
}
