package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.place.dto.CandidateVotes;
import com.poco7250.moyeo.place.dto.RoundVotesResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 득표 현황 조회와 발행. 후보·그룹 멤버·표를 한 번씩 읽어 메모리에서 묶는다.
 */
@Component
@RequiredArgsConstructor
public class PlaceVoteTally {

	private final MeetingAccessChecker meetingAccessChecker;
	private final RoundRepository roundRepository;
	private final PlaceCandidateRepository placeCandidateRepository;
	private final PlaceVoteRepository placeVoteRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 멤버 여부를 검사하고 득표 현황을 돌려준다.
	 *
	 * @throws BusinessException 비멤버·없는 차수 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public RoundVotesResponse read(Long roundId, Long memberId) {
		return tally(meetingAccessChecker.requireRoundMember(roundId, memberId));
	}

	/**
	 * 득표 현황을 읽어 모임 토픽에 VOTE_CHANGED를 발행하고 돌려준다.
	 * 표를 쓴 트랜잭션이 커밋된 뒤 새 트랜잭션에서 불러야 한다. 쓰기 트랜잭션 안에서 읽으면
	 * REPEATABLE READ 스냅샷 때문에 동시에 들어온 표가 빠진 현황이 마지막 메시지로 남을 수 있다.
	 *
	 * @throws BusinessException 없는 차수 ROUND_NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public RoundVotesResponse publish(Long roundId) {
		Round round = roundRepository.findById(roundId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ROUND_NOT_FOUND));
		RoundVotesResponse response = tally(round);
		eventPublisher.publishEvent(new VoteChangedEvent(round.getMeeting().getId(), response));
		return response;
	}

	private RoundVotesResponse tally(Round round) {
		List<GroupMember> members = groupMemberRepository.findAllByGroupIdOrderByIdAsc(
				round.getMeeting().getGroup().getId());
		Map<Long, Set<Long>> voterIdsByCandidate = placeVoteRepository.findAllByRoundId(round.getId()).stream()
				.collect(Collectors.groupingBy(vote -> vote.getCandidate().getId(),
						Collectors.mapping(this::voterId, Collectors.toSet())));
		List<CandidateVotes> candidates = placeCandidateRepository.findAllByRoundIdOrderById(round.getId()).stream()
				.map(candidate -> CandidateVotes.of(candidate.getId(),
						votersOf(members, voterIdsByCandidate.getOrDefault(candidate.getId(), Set.of()))))
				.toList();
		return new RoundVotesResponse(round.getId(), candidates);
	}

	private Long voterId(PlaceVote vote) {
		return vote.getMember().getId();
	}

	/**
	 * 그룹 멤버를 가입 순서 그대로 두고 투표자만 고른다.
	 */
	private List<BoardMember> votersOf(List<GroupMember> members, Set<Long> voterIds) {
		return members.stream()
				.filter(member -> voterIds.contains(member.getMember().getId()))
				.map(BoardMember::from)
				.toList();
	}
}
