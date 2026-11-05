package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.place.dto.RoundVotesResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 장소 후보 투표(PRD P-3). 표 쓰기와 집계를 별도 트랜잭션으로 나눠 커밋된 표로 현황을 발행한다.
 * 실패한 트랜잭션 안에서는 재조회할 수 없어 쓰기 트랜잭션은 PlaceVoteWriter가 호출마다 따로 연다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceVoteService {

	private final PlaceVoteWriter placeVoteWriter;
	private final PlaceVoteTally placeVoteTally;

	/**
	 * 후보에 투표하고 득표 현황을 돌려준다. 표가 새로 저장됐을 때만 VOTE_CHANGED를 발행한다.
	 */
	public RoundVotesResponse vote(Long roundId, Long candidateId, Long memberId) {
		return respond(castWithRetry(roundId, candidateId, memberId), roundId, memberId);
	}

	/**
	 * 내 표를 취소하고 득표 현황을 돌려준다. 표가 실제로 지워졌을 때만 VOTE_CHANGED를 발행한다.
	 */
	public RoundVotesResponse cancel(Long roundId, Long candidateId, Long memberId) {
		return respond(placeVoteWriter.cancel(roundId, candidateId, memberId), roundId, memberId);
	}

	/**
	 * 차수의 득표 현황을 조회한다.
	 */
	public RoundVotesResponse getVotes(Long roundId, Long memberId) {
		return placeVoteTally.read(roundId, memberId);
	}

	/**
	 * 같은 표가 동시에 들어와 유니크 위반이 나면 한 번 더 시도한다(두 번째는 이미 투표함으로 끝난다).
	 */
	private boolean castWithRetry(Long roundId, Long candidateId, Long memberId) {
		try {
			return placeVoteWriter.cast(roundId, candidateId, memberId);
		} catch (DataIntegrityViolationException e) {
			log.info("동시 투표 감지, 재시도: {}", e.getMostSpecificCause().getClass().getSimpleName());
			return placeVoteWriter.cast(roundId, candidateId, memberId);
		}
	}

	private RoundVotesResponse respond(boolean changed, Long roundId, Long memberId) {
		return changed ? placeVoteTally.publish(roundId) : placeVoteTally.read(roundId, memberId);
	}
}
