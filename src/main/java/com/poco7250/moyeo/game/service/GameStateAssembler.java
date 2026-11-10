package com.poco7250.moyeo.game.service;

import com.poco7250.moyeo.game.domain.GameParticipant;
import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.dto.GameParticipantResponse;
import com.poco7250.moyeo.game.dto.GameResultResponse;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameParticipantRepository;
import com.poco7250.moyeo.game.repository.GameResultRepository;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게임 상태 응답을 조립한다. 결과는 공개된 게임에서만 읽어 공개 전 응답에 섞이지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class GameStateAssembler {

	private final GameParticipantRepository gameParticipantRepository;
	private final GameResultRepository gameResultRepository;
	private final GroupMemberRepository groupMemberRepository;

	/**
	 * 게임의 현재 상태 응답을 만든다.
	 */
	public GameStateResponse assemble(GameSession game) {
		return GameStateResponse.of(game, loadParticipants(game), loadResult(game));
	}

	private List<GameParticipantResponse> loadParticipants(GameSession game) {
		List<Long> memberIds = gameParticipantRepository.findAllByGameSessionIdOrderByIdAsc(game.getId()).stream()
				.map(GameParticipant::getMember)
				.map(member -> member.getId())
				.toList();
		Long groupId = game.getRound().getMeeting().getGroup().getId();
		return groupMemberRepository.findAllByGroupIdAndMemberIdIn(groupId, memberIds).stream()
				.map(GameParticipantResponse::from)
				.sorted(Comparator.comparing(GameParticipantResponse::memberId))
				.toList();
	}

	private GameResultResponse loadResult(GameSession game) {
		if (!game.isRevealed()) {
			return null;
		}
		List<Long> penaltyMemberIds = gameResultRepository
				.findAllByGameSessionIdAndOutcome(game.getId(), GameResult.OUTCOME_PENALTY).stream()
				.map(result -> result.getMember().getId())
				.sorted()
				.toList();
		return new GameResultResponse(game.getSeed(), penaltyMemberIds);
	}
}
