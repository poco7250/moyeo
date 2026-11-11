package com.poco7250.moyeo.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameAccessCheckerTest {

	private static final Long GAME_ID = 300L;
	private static final Long MEETING_ID = 10L;
	private static final Long MEMBER_ID = 8L;

	@Mock
	GameSessionRepository gameSessionRepository;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@InjectMocks
	GameAccessChecker gameAccessChecker;

	@Test
	void 그룹_멤버면_진행자가_아니어도_게임을_돌려준다() {
		GameSession game = givenGame();

		assertThat(gameAccessChecker.requireMember(GAME_ID, MEMBER_ID)).isSameAs(game);
	}

	@Test
	void 없는_게임이면_존재를_숨기고_NOT_GROUP_MEMBER() {
		given(gameSessionRepository.findById(GAME_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> gameAccessChecker.requireMember(GAME_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(meetingAccessChecker);
	}

	@Test
	void 비멤버면_NOT_GROUP_MEMBER() {
		givenGame();
		given(meetingAccessChecker.requireMember(MEETING_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertThatThrownBy(() -> gameAccessChecker.requireMember(GAME_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}

	private GameSession givenGame() {
		Meeting meeting = mock(Meeting.class);
		given(meeting.getId()).willReturn(MEETING_ID);
		Round round = mock(Round.class);
		given(round.getMeeting()).willReturn(meeting);
		GameSession game = mock(GameSession.class);
		given(game.getRound()).willReturn(round);
		given(gameSessionRepository.findById(GAME_ID)).willReturn(Optional.of(game));
		return game;
	}
}
