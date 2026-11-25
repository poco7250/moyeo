package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.game.domain.GameParticipant;
import com.poco7250.moyeo.game.repository.GameParticipantRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모임의 금액형·비무효 벌칙과 그 게임들의 참가자를 쿼리 2번으로 읽는다. VOIDED·행동형 벌칙은 담지 않는다(PRD 4.5 무효 벌칙).
 */
@Component
@RequiredArgsConstructor
public class MeetingPenaltyLoader {

	private final PenaltyRepository penaltyRepository;
	private final GameParticipantRepository gameParticipantRepository;

	/**
	 * 모임의 벌칙 묶음을 읽는다.
	 */
	public MeetingPenalties load(Long meetingId) {
		List<Penalty> penalties = penaltyRepository.findAllActiveMoneyByMeetingId(meetingId);
		if (penalties.isEmpty()) {
			return MeetingPenalties.empty();
		}
		Set<Long> gameIds = penalties.stream()
				.map(penalty -> penalty.getGameSession().getId())
				.collect(Collectors.toSet());
		Map<Long, List<Member>> participantsByGameId = gameParticipantRepository
				.findAllWithMemberByGameSessionIdIn(gameIds).stream()
				.collect(Collectors.groupingBy(participant -> participant.getGameSession().getId(),
						Collectors.mapping(GameParticipant::getMember, Collectors.toList())));
		return new MeetingPenalties(penalties, participantsByGameId);
	}
}
