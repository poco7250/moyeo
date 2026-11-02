package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.config.MeetingLinkProperties;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.GroupMeetingsResponse;
import com.poco7250.moyeo.meeting.dto.MeetingCreateRequest;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.dto.MeetingSummaryResponse;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 생성·조회.
 */
@Service
@RequiredArgsConstructor
public class MeetingService {

	private final MeetingRepository meetingRepository;
	private final RoundRepository roundRepository;
	private final SettlementRepository settlementRepository;
	private final GroupRepository groupRepository;
	private final GroupAccessChecker groupAccessChecker;
	private final MeetingAccessChecker meetingAccessChecker;
	private final MeetingLinkProperties meetingLinkProperties;
	private final Clock clock;

	/**
	 * 모임을 만든다. 요청자가 호스트가 되고, 차수와 미확정(DRAFT) 정산이 한 트랜잭션에서 함께 생긴다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional
	public MeetingDetailResponse create(Long groupId, Long memberId, MeetingCreateRequest request) {
		groupAccessChecker.requireMember(groupId, memberId);
		Group group = groupRepository.getReferenceById(groupId);

		Meeting meeting = meetingRepository.save(Meeting.create(group, request.title(), request.startAt(),
				request.memo(), request.responseDeadline(), memberId));
		List<Round> rounds = roundRepository.saveAll(createRounds(meeting, request.resolveRoundNames()));
		settlementRepository.save(Settlement.createDraft(meeting));
		return toDetail(meeting, rounds, memberId);
	}

	/**
	 * 모임 상세(차수·장소 상태·호스트 여부)를 조회한다.
	 *
	 * @throws BusinessException 없는 모임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public MeetingDetailResponse getDetail(Long meetingId, Long memberId) {
		Meeting meeting = meetingAccessChecker.requireMember(meetingId, memberId);
		List<Round> rounds = roundRepository.findAllByMeetingIdOrderBySeq(meetingId);
		return toDetail(meeting, rounds, memberId);
	}

	/**
	 * 그룹의 모임을 현재 시각 기준 다가오는 모임과 지난 모임으로 나눠 조회한다. 시작 시각 정각은 다가오는 쪽이다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public GroupMeetingsResponse getGroupMeetings(Long groupId, Long memberId) {
		groupAccessChecker.requireMember(groupId, memberId);
		LocalDateTime now = LocalDateTime.now(clock);
		return new GroupMeetingsResponse(
				toSummaries(meetingRepository.findAllByGroupIdAndStartAtGreaterThanEqualOrderByStartAtAsc(groupId, now)),
				toSummaries(meetingRepository.findAllByGroupIdAndStartAtLessThanOrderByStartAtDesc(groupId, now)));
	}

	/**
	 * 이름 목록으로 seq 1부터 차수를 만든다.
	 */
	private List<Round> createRounds(Meeting meeting, List<String> names) {
		return IntStream.range(0, names.size())
				.mapToObj(i -> Round.create(meeting, i + 1, names.get(i)))
				.toList();
	}

	private MeetingDetailResponse toDetail(Meeting meeting, List<Round> rounds, Long memberId) {
		return MeetingDetailResponse.of(meeting, rounds, memberId, meetingLinkProperties.urlOf(meeting.getId()));
	}

	private List<MeetingSummaryResponse> toSummaries(List<Meeting> meetings) {
		return meetings.stream().map(MeetingSummaryResponse::from).toList();
	}
}
