package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingDateCandidate;
import com.poco7250.moyeo.meeting.domain.MeetingDateVote;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.DateCandidateVotes;
import com.poco7250.moyeo.meeting.dto.MeetingDateVotesResponse;
import com.poco7250.moyeo.meeting.repository.MeetingDateCandidateRepository;
import com.poco7250.moyeo.meeting.repository.MeetingDateVoteRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 날짜 투표 현황을 집계한다. 실시간 발행은 쓰기 트랜잭션이 커밋된 뒤 새 트랜잭션에서 읽어 보낸다
 * (쓰기 트랜잭션 안에서 읽으면 동시에 들어온 응답이 빠진 현황이 마지막 메시지로 남을 수 있다 — 장소 투표와 같은 이유).
 */
@Component
@RequiredArgsConstructor
public class MeetingDateTally {

	private final MeetingAccessChecker meetingAccessChecker;
	private final MeetingRepository meetingRepository;
	private final MeetingDateCandidateRepository candidateRepository;
	private final MeetingDateVoteRepository voteRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 멤버 여부를 검사하고 날짜 투표 현황을 돌려준다.
	 *
	 * @throws BusinessException 비멤버·없는 모임 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public MeetingDateVotesResponse read(Long meetingId, Long memberId) {
		return tally(meetingAccessChecker.requireMember(meetingId, memberId));
	}

	/**
	 * 현황을 읽어 모임 토픽에 DATE_VOTE_CHANGED를 발행하고 돌려준다. 쓰기 트랜잭션 밖에서 불러야 한다.
	 *
	 * @throws BusinessException 없는 모임 MEETING_NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public MeetingDateVotesResponse publish(Long meetingId) {
		Meeting meeting = meetingRepository.findById(meetingId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
		MeetingDateVotesResponse response = tally(meeting);
		eventPublisher.publishEvent(new DateVoteChangedEvent(meetingId, response));
		return response;
	}

	private MeetingDateVotesResponse tally(Meeting meeting) {
		List<GroupMember> members = groupMemberRepository.findAllByGroupIdOrderByIdAsc(meeting.getGroup().getId());
		Map<Long, Map<Long, Boolean>> answersByCandidate = voteRepository.findAllByMeetingId(meeting.getId()).stream()
				.collect(Collectors.groupingBy(vote -> vote.getCandidate().getId(),
						Collectors.toMap(vote -> vote.getMember().getId(), MeetingDateVote::isAvailable)));
		List<MeetingDateCandidate> candidates = candidateRepository.findAllByMeetingIdOrderByStartAtAscIdAsc(
				meeting.getId());
		List<DateCandidateVotes> votes = candidates.stream()
				.map(candidate -> votesOf(candidate, members, answersByCandidate.getOrDefault(candidate.getId(), Map.of())))
				.toList();
		return new MeetingDateVotesResponse(meeting.getId(), meeting.getDateStatus(), meeting.getStartAt(),
				confirmedCandidateId(meeting, candidates), votes);
	}

	/**
	 * 그룹 멤버를 가입 순서 그대로 가능·불가·미응답으로 나눈다.
	 */
	private DateCandidateVotes votesOf(MeetingDateCandidate candidate, List<GroupMember> members,
			Map<Long, Boolean> answers) {
		return DateCandidateVotes.of(candidate.getId(), candidate.getStartAt(),
				membersWhere(members, answers, Boolean.TRUE),
				membersWhere(members, answers, Boolean.FALSE),
				membersWhere(members, answers, null));
	}

	private List<BoardMember> membersWhere(List<GroupMember> members, Map<Long, Boolean> answers, Boolean answer) {
		return members.stream()
				.filter(member -> Objects.equals(answers.get(member.getMember().getId()), answer))
				.map(BoardMember::from)
				.toList();
	}

	// 같은 모임에 같은 시각 후보는 하나뿐이라 확정 시각으로 후보를 찾는다
	private Long confirmedCandidateId(Meeting meeting, List<MeetingDateCandidate> candidates) {
		if (meeting.isDateVoting()) {
			return null;
		}
		return candidates.stream()
				.filter(candidate -> candidate.getStartAt().equals(meeting.getStartAt()))
				.map(MeetingDateCandidate::getId)
				.findFirst()
				.orElse(null);
	}
}
