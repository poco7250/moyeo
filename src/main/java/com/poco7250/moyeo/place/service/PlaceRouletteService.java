package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.game.service.GameSeedGenerator;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.meeting.service.MeetingPushPublisher;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceRoulette;
import com.poco7250.moyeo.place.dto.PlaceRouletteResponse;
import com.poco7250.moyeo.place.dto.PlaceRouletteResultResponse;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.repository.PlaceRouletteRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 투표 동점을 룰렛으로 정한다(PRD P-6). 호스트 수동 확정과 같은 Round.version 낙관적 락을 쓰므로
 * 둘이 겹치면 늦은 쪽이 409가 되고 그 쪽의 룰렛 기록도 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class PlaceRouletteService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final RoundRepository roundRepository;
	private final RoundVoteDecider roundVoteDecider;
	private final PlaceRouletteRepository placeRouletteRepository;
	private final MemberRepository memberRepository;
	private final GameSeedGenerator seedGenerator;
	private final RoundPlaceSnapshot roundPlaceSnapshot;
	private final MeetingPushPublisher meetingPushPublisher;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * 호스트 선택 대기 중인 차수의 동점 후보(전원 0표면 후보 전체) 중 하나를 뽑아 바로 확정하고 기록을 남긴다.
	 *
	 * @throws BusinessException 호스트 아님 NOT_MEETING_HOST, 선택 대기가 아니면 ROUND_PLACE_INVALID_TRANSITION,
	 *         대상 후보가 2개 미만이면 PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES
	 */
	@Transactional
	public PlaceRouletteResultResponse spin(Long roundId, Long hostId) {
		Round round = meetingAccessChecker.requireRoundHost(roundId, hostId);
		if (!round.isAwaitingHostDecision()) {
			throw new BusinessException(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		}
		PlaceRoulette roulette = PlaceRoulette.spin(round, roundVoteDecider.decide(roundId).tiedCandidates(),
				seedGenerator.next(), memberRepository.getReferenceById(hostId), LocalDateTime.now(clock));
		round.confirm(roulette.getWinner());
		// 기록 insert의 FK 공유 잠금을 먼저 잡으면 수동 확정의 차수 갱신과 잠금 대기·데드락이 나므로 version 갱신을 먼저 보낸다
		roundRepository.flush();
		placeRouletteRepository.save(roulette);
		meetingPushPublisher.placeConfirmed(round, hostId);
		RoundPlaceResponse place = roundPlaceSnapshot.publish(round);
		PlaceRouletteResponse response = PlaceRouletteResponse.from(roulette);
		eventPublisher.publishEvent(new PlaceRouletteSpunEvent(round.getMeeting().getId(), response));
		return new PlaceRouletteResultResponse(place, response);
	}

	/**
	 * 차수의 룰렛 기록을 최근 순으로 돌려준다(재투표 뒤 다시 돌렸으면 여러 건).
	 *
	 * @throws BusinessException 비멤버·없는 차수 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<PlaceRouletteResponse> getHistory(Long roundId, Long memberId) {
		meetingAccessChecker.requireRoundMember(roundId, memberId);
		return placeRouletteRepository.findAllByRoundIdOrderByIdDesc(roundId).stream()
				.map(PlaceRouletteResponse::from)
				.toList();
	}
}
