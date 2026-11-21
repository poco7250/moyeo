package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingDateCandidate;
import com.poco7250.moyeo.meeting.domain.MeetingDateVote;
import com.poco7250.moyeo.meeting.dto.MeetingDateConfirmedResponse;
import com.poco7250.moyeo.meeting.repository.MeetingDateCandidateRepository;
import com.poco7250.moyeo.meeting.repository.MeetingDateVoteRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 날짜 투표 쓰기(호출 한 번 = 트랜잭션 하나). 유니크 위반 재시도와 현황 발행은 MeetingDateService가 트랜잭션 밖에서 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingDateWriter {

	private final MeetingAccessChecker meetingAccessChecker;
	private final MeetingDateCandidateRepository candidateRepository;
	private final MeetingDateVoteRepository voteRepository;
	private final MemberRepository memberRepository;
	private final MeetingPushPublisher meetingPushPublisher;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 날짜 투표 중인 모임에 후보를 추가한다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 호스트 아님 NOT_MEETING_HOST, 투표 중 아님 MEETING_DATE_NOT_VOTING,
	 *         후보 10개 초과 MEETING_DATE_CANDIDATE_LIMIT, 같은 시각 후보 있음 MEETING_DATE_CANDIDATE_DUPLICATED
	 */
	@Transactional
	public void addCandidate(Long meetingId, Long hostId, LocalDateTime startAt) {
		Meeting meeting = meetingAccessChecker.requireHost(meetingId, hostId);
		meeting.requireDateVoting();
		if (candidateRepository.countByMeetingId(meetingId) >= MeetingDateCandidate.MAX_PER_MEETING) {
			throw new BusinessException(ErrorCode.MEETING_DATE_CANDIDATE_LIMIT);
		}
		if (candidateRepository.existsByMeetingIdAndStartAt(meetingId, startAt)) {
			throw new BusinessException(ErrorCode.MEETING_DATE_CANDIDATE_DUPLICATED);
		}
		// 같은 시각이 동시에 들어온 경우의 유니크 위반을 이 트랜잭션 안에서 드러낸다
		candidateRepository.saveAndFlush(MeetingDateCandidate.propose(meeting, startAt));
	}

	/**
	 * 요청자의 응답을 모임의 모든 후보에 대해 가능/불가로 맞춘다. 같은 응답이면 아무것도 쓰지 않는다(멱등).
	 *
	 * @return 실제로 바뀐 응답이 있으면 true
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 투표 중 아님 MEETING_DATE_NOT_VOTING,
	 *         이 모임의 후보가 아닌 id MEETING_DATE_CANDIDATE_NOT_FOUND
	 */
	@Transactional
	public boolean respond(Long meetingId, Long memberId, List<Long> availableCandidateIds) {
		Meeting meeting = meetingAccessChecker.requireMember(meetingId, memberId);
		meeting.requireDateVoting();
		List<MeetingDateCandidate> candidates = candidateRepository.findAllByMeetingIdOrderByStartAtAscIdAsc(meetingId);
		Set<Long> available = requireOwnCandidates(candidates, availableCandidateIds);
		Map<Long, MeetingDateVote> mine = voteRepository.findAllByMeetingIdAndMemberId(meetingId, memberId).stream()
				.collect(Collectors.toMap(vote -> vote.getCandidate().getId(), Function.identity()));

		boolean changed = false;
		for (MeetingDateCandidate candidate : candidates) {
			changed |= answer(mine.get(candidate.getId()), candidate, memberId, available.contains(candidate.getId()));
		}
		return changed;
	}

	/**
	 * 호스트가 고른 후보로 날짜를 확정하고 MEETING_DATE_CONFIRMED와 날짜 확정 웹 푸시를 발행한다(커밋 후 전송).
	 * 확정끼리 동시에 들어오면 Meeting.version 충돌로 늦은 쪽이 실패한다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 호스트 아님 NOT_MEETING_HOST,
	 *         이 모임의 후보가 아니면 MEETING_DATE_CANDIDATE_NOT_FOUND, 투표 중 아님 MEETING_DATE_NOT_VOTING
	 */
	@Transactional
	public void confirm(Long meetingId, Long hostId, Long candidateId) {
		Meeting meeting = meetingAccessChecker.requireHost(meetingId, hostId);
		MeetingDateCandidate candidate = candidateRepository.findByIdAndMeetingId(candidateId, meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETING_DATE_CANDIDATE_NOT_FOUND));
		meeting.confirmDate(candidate);
		log.info("모임 날짜 확정: meetingId={}, candidateId={}", meetingId, candidateId);
		eventPublisher.publishEvent(new MeetingDateConfirmedEvent(meetingId,
				new MeetingDateConfirmedResponse(meetingId, candidateId, candidate.getStartAt())));
		meetingPushPublisher.dateConfirmed(meeting, hostId);
	}

	/**
	 * 요청한 id가 모두 이 모임의 후보인지 확인하고 집합으로 돌려준다.
	 */
	private Set<Long> requireOwnCandidates(List<MeetingDateCandidate> candidates, List<Long> requested) {
		Set<Long> own = candidates.stream().map(MeetingDateCandidate::getId).collect(Collectors.toSet());
		Set<Long> available = new HashSet<>(requested);
		if (!own.containsAll(available)) {
			throw new BusinessException(ErrorCode.MEETING_DATE_CANDIDATE_NOT_FOUND);
		}
		return available;
	}

	/**
	 * 후보 하나의 응답을 저장한다. 없으면 만들고, 있으면 값이 다를 때만 바꾼다.
	 *
	 * @return 새로 만들었거나 바뀌었으면 true
	 */
	private boolean answer(MeetingDateVote current, MeetingDateCandidate candidate, Long memberId, boolean available) {
		if (current != null) {
			return current.change(available);
		}
		Member member = memberRepository.getReferenceById(memberId);
		voteRepository.saveAndFlush(MeetingDateVote.answer(candidate, member, available));
		return true;
	}
}
