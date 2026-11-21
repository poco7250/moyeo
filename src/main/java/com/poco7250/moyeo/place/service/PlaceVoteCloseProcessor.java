package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.meeting.service.MeetingPushPublisher;
import com.poco7250.moyeo.place.dto.PlaceDecisionRequiredResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차수 하나의 투표 마감 처리 트랜잭션. 수동 확정과 겹치면 Round.version 낙관적 락으로 늦은 쪽이 실패한다.
 */
@Component
@RequiredArgsConstructor
public class PlaceVoteCloseProcessor {

	private final RoundRepository roundRepository;
	private final RoundVoteDecider roundVoteDecider;
	private final RoundPlaceSnapshot roundPlaceSnapshot;
	private final MeetingPushPublisher meetingPushPublisher;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * 단독 최다면 그 후보로 확정하고, 아니면 호스트 선택 대기로 넘긴다.
	 * 조회 뒤 다른 쪽이 먼저 처리했을 수 있어 상태를 다시 확인하고, 이미 처리됐으면 아무것도 하지 않는다.
	 */
	@Transactional
	public void close(Long roundId) {
		LocalDateTime now = LocalDateTime.now(clock);
		roundRepository.findById(roundId)
				.filter(round -> round.isVoteCloseDue(now))
				.ifPresent(round -> applyDecision(round, now));
	}

	private void applyDecision(Round round, LocalDateTime now) {
		RoundVoteDecider.Result result = roundVoteDecider.decide(round.getId());
		if (result.decision().hasWinner()) {
			round.confirm(result.winner());
			// 자동 마감은 확정한 사람이 없어 불참자만 뺀다
			meetingPushPublisher.placeConfirmed(round, null);
			roundPlaceSnapshot.publish(round);
			return;
		}
		round.markVoteClosed(now);
		eventPublisher.publishEvent(new PlaceDecisionRequiredEvent(round.getMeeting().getId(),
				new PlaceDecisionRequiredResponse(round.getId(), result.decision().tiedCandidateIds())));
	}
}
