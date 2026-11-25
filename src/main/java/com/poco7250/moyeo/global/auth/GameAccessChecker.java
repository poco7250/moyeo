package com.poco7250.moyeo.global.auth;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게임 리소스 인가 공통 검사. 검사를 통과하면 조회한 게임을 돌려줘 Service가 다시 읽지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class GameAccessChecker {

	private final GameSessionRepository gameSessionRepository;
	private final MeetingAccessChecker meetingAccessChecker;

	/**
	 * 회원이 게임이 속한 그룹의 멤버인지 검사하고 게임을 돌려준다.
	 * 없는 게임도 같은 403으로 응답해 게임 존재 여부를 드러내지 않는다.
	 *
	 * @throws BusinessException 없는 게임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	public GameSession requireMember(Long gameId, Long memberId) {
		GameSession game = findGame(gameId);
		meetingAccessChecker.requireMember(game.getMeeting().getId(), memberId);
		return game;
	}

	/**
	 * 회원이 게임의 진행자이거나 모임 호스트 권한자(호스트·방장)인지 검사하고 게임을 돌려준다.
	 * 없는 게임도 같은 403으로 응답해 게임 존재 여부를 드러내지 않는다.
	 *
	 * @throws BusinessException 없는 게임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 진행자·호스트가 아니면 NOT_GAME_OPERATOR
	 */
	public GameSession requireOperator(Long gameId, Long memberId) {
		GameSession game = findGame(gameId);
		Meeting meeting = meetingAccessChecker.requireMember(game.getMeeting().getId(), memberId);
		if (!game.isOperator(memberId, meetingAccessChecker.hasHostAuthority(meeting, memberId))) {
			throw new BusinessException(ErrorCode.NOT_GAME_OPERATOR);
		}
		return game;
	}

	/**
	 * 회원이 게임이 속한 모임의 정산 권한(호스트·방장·총무)을 가졌는지 검사하고 게임을 돌려준다(벌칙 조건용).
	 * 진행자여도 정산 권한이 없으면 통과하지 못한다. 없는 게임도 같은 403으로 응답해 게임 존재 여부를 드러내지 않는다.
	 *
	 * @throws BusinessException 없는 게임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 정산 권한이 없으면 NOT_MEETING_HOST
	 */
	public GameSession requireSettlementManager(Long gameId, Long memberId) {
		GameSession game = findGame(gameId);
		meetingAccessChecker.requireSettlementManager(game.getMeeting().getId(), memberId);
		return game;
	}

	private GameSession findGame(Long gameId) {
		return gameSessionRepository.findById(gameId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
	}
}
