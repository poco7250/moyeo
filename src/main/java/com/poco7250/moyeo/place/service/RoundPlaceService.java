package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 호스트의 차수 장소 결정(바로 확정·투표 열기). Round.version 낙관적 락으로 동시 변경 중 늦은 쪽은 409가 된다.
 */
@Service
@RequiredArgsConstructor
public class RoundPlaceService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final PlaceCandidateRepository placeCandidateRepository;
	private final PlaceVoteRepository placeVoteRepository;
	private final MemberRepository memberRepository;
	private final RoundPlaceSnapshot roundPlaceSnapshot;

	/**
	 * 투표 없이 장소를 바로 확정한다. 후보 1개짜리 PlaceCandidate를 만들고 CONFIRMED로 바꾼다(PRD P-2).
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException 호스트 아님 NOT_MEETING_HOST,
	 *         미정 차수가 아니면 ROUND_PLACE_INVALID_TRANSITION
	 */
	@Transactional
	public RoundPlaceResponse confirmDirectly(Long roundId, Long hostId, PlaceInput input) {
		Round round = meetingAccessChecker.requireRoundHost(roundId, hostId);
		// 전이 불가면 후보를 저장하기 전에 막는다
		round.requireUndecided();
		PlaceCandidate candidate = placeCandidateRepository.save(
				PlaceCandidate.propose(round, input.toPlace(), memberRepository.getReferenceById(hostId)));
		round.confirmDirectly(candidate);
		return roundPlaceSnapshot.publish(round);
	}

	/**
	 * 장소 투표를 연다. 확정된 차수면 재투표로, 후보는 남기고 표만 지운다.
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException 호스트 아님 NOT_MEETING_HOST,
	 *         이미 투표 중이면 ROUND_PLACE_INVALID_TRANSITION
	 */
	@Transactional
	public RoundPlaceResponse openVoting(Long roundId, Long hostId, LocalDateTime deadline) {
		Round round = meetingAccessChecker.requireRoundHost(roundId, hostId);
		boolean reopening = round.getPlaceStatus() == PlaceStatus.CONFIRMED;
		round.openVoting(deadline);
		if (reopening) {
			placeVoteRepository.deleteAllByRoundId(round.getId());
		}
		return roundPlaceSnapshot.publish(round);
	}
}
