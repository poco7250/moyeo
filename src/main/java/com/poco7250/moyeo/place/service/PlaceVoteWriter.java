package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표 저장·삭제 트랜잭션. 유니크 위반 재시도와 커밋 후 집계는 PlaceVoteService가 맡는다.
 */
@Component
@RequiredArgsConstructor
public class PlaceVoteWriter {

	private final MeetingAccessChecker meetingAccessChecker;
	private final PlaceCandidateRepository placeCandidateRepository;
	private final PlaceVoteRepository placeVoteRepository;
	private final MemberRepository memberRepository;
	private final Clock clock;

	/**
	 * 후보에 투표한다. 이미 투표했으면 아무것도 바꾸지 않는다(멱등).
	 *
	 * @return 표가 새로 저장됐으면 true
	 * @throws BusinessException 비멤버·없는 차수 NOT_GROUP_MEMBER, 투표 중이 아니면 ROUND_PLACE_INVALID_TRANSITION,
	 *         마감 후 PLACE_VOTE_DEADLINE_PASSED, 이 차수의 후보가 아니면 PLACE_CANDIDATE_NOT_FOUND
	 */
	@Transactional
	public boolean cast(Long roundId, Long candidateId, Long memberId) {
		PlaceCandidate candidate = requireVotableCandidate(roundId, candidateId, memberId);
		if (placeVoteRepository.existsByCandidateIdAndMemberId(candidateId, memberId)) {
			return false;
		}
		placeVoteRepository.save(PlaceVote.cast(candidate, memberRepository.getReferenceById(memberId)));
		return true;
	}

	/**
	 * 내 표를 취소한다. 표가 없으면 아무것도 바꾸지 않는다(멱등).
	 *
	 * @return 표가 실제로 지워졌으면 true
	 * @throws BusinessException 투표와 같은 조건으로 거부한다
	 */
	@Transactional
	public boolean cancel(Long roundId, Long candidateId, Long memberId) {
		requireVotableCandidate(roundId, candidateId, memberId);
		return placeVoteRepository.deleteByCandidateIdAndMemberId(candidateId, memberId) > 0;
	}

	private PlaceCandidate requireVotableCandidate(Long roundId, Long candidateId, Long memberId) {
		Round round = meetingAccessChecker.requireRoundMember(roundId, memberId);
		round.requireVoteOpen(LocalDateTime.now(clock));
		return placeCandidateRepository.findByIdAndRoundId(candidateId, roundId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PLACE_CANDIDATE_NOT_FOUND));
	}
}
