package com.poco7250.moyeo.game.service;

import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.auth.GameAccessChecker;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임 상태 조회. 연결이 끊겼거나 실시간 알림을 놓친 멤버가 REST로 화면을 복구할 때 쓴다.
 * 결과는 GameStateAssembler가 공개된 게임에서만 채운다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GameQueryService {

	private final GameAccessChecker gameAccessChecker;
	private final MeetingAccessChecker meetingAccessChecker;
	private final GameSessionRepository gameSessionRepository;
	private final GameStateAssembler gameStateAssembler;

	/**
	 * 게임의 현재 상태를 조회한다. 공개 전이면 결과가 없다.
	 *
	 * @throws BusinessException 없는 게임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	public GameStateResponse getState(Long gameId, Long memberId) {
		return gameStateAssembler.assemble(gameAccessChecker.requireMember(gameId, memberId));
	}

	/**
	 * 차수의 게임 상태 목록을 최근에 만든 순으로 조회한다(취소·종료된 게임 포함).
	 *
	 * @throws BusinessException 없는 차수이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	public List<GameStateResponse> getRoundGames(Long roundId, Long memberId) {
		meetingAccessChecker.requireRoundMember(roundId, memberId);
		// 한 차수의 게임은 몇 건뿐이라 게임마다 참가자·결과를 따로 읽는다
		return gameSessionRepository.findAllByRoundIdOrderByIdDesc(roundId).stream()
				.map(gameStateAssembler::assemble)
				.toList();
	}
}
