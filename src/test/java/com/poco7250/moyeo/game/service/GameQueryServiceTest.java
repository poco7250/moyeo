package com.poco7250.moyeo.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameResultResponse;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.auth.GameAccessChecker;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameQueryServiceTest {

	private static final Long ROUND_ID = 100L;
	private static final Long MEMBER_ID = 8L;

	@Mock
	GameAccessChecker gameAccessChecker;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	GameSessionRepository gameSessionRepository;

	@Mock
	GameStateAssembler gameStateAssembler;

	@InjectMocks
	GameQueryService service;

	@Test
	@DisplayName("그룹 멤버면 게임의 현재 상태를 돌려준다")
	void getState() {
		GameSession game = mock(GameSession.class);
		GameStateResponse state = state(300L, GameStatus.REVEALED, new GameResultResponse(42L, List.of(9L)));
		given(gameAccessChecker.requireMember(300L, MEMBER_ID)).willReturn(game);
		given(gameStateAssembler.assemble(game)).willReturn(state);

		assertThat(service.getState(300L, MEMBER_ID)).isSameAs(state);
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 상태를 조립하지 않고 NOT_GROUP_MEMBER다")
	void getStateRejectsNonMember() {
		given(gameAccessChecker.requireMember(300L, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertThatThrownBy(() -> service.getState(300L, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(gameStateAssembler);
	}

	@Test
	@DisplayName("차수 게임 목록은 저장소가 준 순서대로 상태를 조립한다")
	void getRoundGames() {
		GameSession latest = mock(GameSession.class);
		GameSession oldest = mock(GameSession.class);
		GameStateResponse latestState = state(301L, GameStatus.WAITING, null);
		GameStateResponse oldestState = state(300L, GameStatus.CLOSED, new GameResultResponse(42L, List.of(9L)));
		given(gameSessionRepository.findAllByRoundIdOrderByIdDesc(ROUND_ID)).willReturn(List.of(latest, oldest));
		given(gameStateAssembler.assemble(latest)).willReturn(latestState);
		given(gameStateAssembler.assemble(oldest)).willReturn(oldestState);

		assertThat(service.getRoundGames(ROUND_ID, MEMBER_ID)).containsExactly(latestState, oldestState);
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 차수 게임을 읽지 않고 NOT_GROUP_MEMBER다")
	void getRoundGamesRejectsNonMember() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertThatThrownBy(() -> service.getRoundGames(ROUND_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(gameSessionRepository, gameStateAssembler);
	}

	private GameStateResponse state(Long gameId, GameStatus status, GameResultResponse result) {
		return new GameStateResponse(gameId, ROUND_ID, GameType.LADDER, status, MEMBER_ID, PenaltyKind.NONE, null, 1,
				gameId, null, null, List.of(), result);
	}
}
