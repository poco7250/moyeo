package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.dto.CandidateResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceRouletteRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 후보 추가·삭제. 마감 전 투표 중인 차수에 그룹 멤버 누구나 사용자 입력 장소로 후보를 올리고(PRD P-1·P-2),
 * 잘못 올린 후보는 투표 중에 호스트가 지운다.
 */
@Service
@RequiredArgsConstructor
public class PlaceCandidateService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final PlaceCandidateRepository placeCandidateRepository;
	private final PlaceVoteRepository placeVoteRepository;
	private final PlaceRouletteRepository placeRouletteRepository;
	private final MemberRepository memberRepository;
	private final RoundPlaceSnapshot roundPlaceSnapshot;
	private final EntityManager entityManager;
	private final Clock clock;

	/**
	 * 마감 전 투표 중인 차수에 후보를 추가하고 PLACE_CHANGED를 발행한다.
	 *
	 * @throws BusinessException 비멤버·없는 차수 NOT_GROUP_MEMBER,
	 *         투표 중이 아니면 ROUND_PLACE_INVALID_TRANSITION, 마감 후 PLACE_VOTE_DEADLINE_PASSED
	 */
	@Transactional
	public CandidateResponse add(Long roundId, Long memberId, PlaceInput input) {
		Round round = meetingAccessChecker.requireRoundMember(roundId, memberId);
		round.requireVoteOpen(LocalDateTime.now(clock));
		PlaceCandidate candidate = placeCandidateRepository.save(
				PlaceCandidate.propose(round, input.toPlace(), memberRepository.getReferenceById(memberId)));
		roundPlaceSnapshot.publish(round);
		return CandidateResponse.from(candidate);
	}

	/**
	 * 투표 중인 차수의 후보를 그 후보에 달린 표와 함께 지우고 PLACE_CHANGED를 발행한다.
	 * 마감 뒤 호스트 선택 대기 중에도 지울 수 있다. 차수 version을 올려 같은 차수의 확정·마감 처리·룰렛과
	 * 겹치면 늦은 쪽이 낙관적 락으로 409가 된다(확정 장소로 지운 후보가 가리켜지지 않게).
	 *
	 * @throws BusinessException 호스트 아님 NOT_MEETING_HOST, 투표 중이 아니면 ROUND_PLACE_INVALID_TRANSITION,
	 *         이 차수의 후보가 아니면 PLACE_CANDIDATE_NOT_FOUND, 룰렛 기록에 남은 후보면 PLACE_CANDIDATE_IN_ROULETTE
	 */
	@Transactional
	public RoundPlaceResponse delete(Long roundId, Long hostId, Long candidateId) {
		Round round = meetingAccessChecker.requireRoundHost(roundId, hostId);
		round.requireVoting();
		PlaceCandidate candidate = placeCandidateRepository.findByIdAndRoundId(candidateId, roundId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLACE_CANDIDATE_NOT_FOUND));
		if (placeRouletteRepository.existsByCandidateId(candidateId)) {
			throw new BusinessException(ErrorCode.PLACE_CANDIDATE_IN_ROULETTE);
		}
		entityManager.lock(round, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		placeVoteRepository.deleteAllByCandidateId(candidateId);
		placeCandidateRepository.delete(candidate);
		return roundPlaceSnapshot.publish(round);
	}
}
