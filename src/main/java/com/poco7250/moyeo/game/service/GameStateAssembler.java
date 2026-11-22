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
import com.poco7250.moyeo.member.domain.Member;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
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

	/**
	 * 참가자를 회원 id 순으로 돌려준다. 그룹 멤버는 그룹 프로필, 게스트는 게스트 이름으로 만든다.
	 */
	private List<GameParticipantResponse> loadParticipants(GameSession game) {
		List<Member> members = gameParticipantRepository.findAllByGameSessionIdOrderByIdAsc(game.getId()).stream()
				.map(GameParticipant::getMember)
				.toList();
		List<Long> memberIds = members.stream().filter(member -> !member.isGuest()).map(Member::getId).toList();
		Long groupId = game.getRound().getMeeting().getGroup().getId();
		Stream<GameParticipantResponse> groupMembers = memberIds.isEmpty() ? Stream.empty()
				: groupMemberRepository.findAllByGroupIdAndMemberIdIn(groupId, memberIds).stream()
						.map(GameParticipantResponse::from);
		Stream<GameParticipantResponse> guests = members.stream().filter(Member::isGuest)
				.map(GameParticipantResponse::guest);
		return Stream.concat(groupMembers, guests)
				.sorted(Comparator.comparing(GameParticipantResponse::memberId))
				.toList();
	}

	private GameResultResponse loadResult(GameSession game) {
		if (!game.isRevealed()) {
			return null;
		}
		if (game.getType().isOrder()) {
			return GameResultResponse.order(game.getSeed(), gameResultRepository
					.findAllByGameSessionIdAndOutcomeOrderByOrderNoAsc(game.getId(), GameResult.OUTCOME_ORDERED)
					.stream().map(result -> result.getMember().getId()).toList());
		}
		List<Long> penaltyMemberIds = gameResultRepository
				.findAllByGameSessionIdAndOutcome(game.getId(), GameResult.OUTCOME_PENALTY).stream()
				.map(result -> result.getMember().getId())
				.sorted()
				.toList();
		return GameResultResponse.penalty(game.getSeed(), penaltyMemberIds);
	}
}
