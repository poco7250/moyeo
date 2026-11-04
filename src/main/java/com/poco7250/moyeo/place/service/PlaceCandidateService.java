package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.dto.CandidateResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 후보 추가. 투표 중인 차수에 그룹 멤버 누구나 사용자 입력 장소로 후보를 올린다(PRD P-1·P-2).
 */
@Service
@RequiredArgsConstructor
public class PlaceCandidateService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final PlaceCandidateRepository placeCandidateRepository;
	private final MemberRepository memberRepository;
	private final RoundPlaceSnapshot roundPlaceSnapshot;

	/**
	 * 투표 중인 차수에 후보를 추가하고 PLACE_CHANGED를 발행한다.
	 *
	 * @throws com.poco7250.moyeo.global.error.BusinessException 비멤버·없는 차수 NOT_GROUP_MEMBER,
	 *         투표 중이 아니면 ROUND_PLACE_INVALID_TRANSITION
	 */
	@Transactional
	public CandidateResponse add(Long roundId, Long memberId, PlaceInput input) {
		Round round = meetingAccessChecker.requireRoundMember(roundId, memberId);
		round.requireVoting();
		PlaceCandidate candidate = placeCandidateRepository.save(
				PlaceCandidate.propose(round, input.toPlace(), memberRepository.getReferenceById(memberId)));
		roundPlaceSnapshot.publish(round);
		return CandidateResponse.from(candidate);
	}
}
