package com.poco7250.moyeo.history.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.history.dto.CardShareResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.MeetingCardResponse;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 결산 카드 조회(PRD H-3). 히스토리 요약에 마지막 확정 리비전의 1인당 부담액을 붙인다.
 * 읽기 전용이라 쓰기·실시간 이벤트가 없다.
 */
@Service
@RequiredArgsConstructor
public class MeetingCardService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final MeetingHistoryService meetingHistoryService;
	private final SettlementRepository settlementRepository;
	private final SettlementRevisionRepository settlementRevisionRepository;
	private final RevisionMemberShareRepository revisionMemberShareRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final Clock clock;

	/**
	 * 지난 모임(시작 시각이 지금 이전)의 결산 카드 데이터를 돌려준다.
	 *
	 * @throws BusinessException 없는 모임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER,
	 *         날짜 투표 중이거나 아직 시작 전이면 MEETING_NOT_PAST
	 */
	@Transactional(readOnly = true)
	public MeetingCardResponse get(Long meetingId, Long memberId) {
		Meeting meeting = meetingAccessChecker.requireMember(meetingId, memberId);
		if (meeting.getStartAt() == null || !meeting.getStartAt().isBefore(LocalDateTime.now(clock))) {
			throw new BusinessException(ErrorCode.MEETING_NOT_PAST);
		}
		Long groupId = meeting.getGroup().getId();
		return new MeetingCardResponse(meeting.getGroup().getName(),
				meetingHistoryService.historyOf(groupId, meeting), shares(groupId, meetingId));
	}

	// 마지막 확정 리비전의 부담액을 금액 많은 순 → 가입 순으로. 확정된 적 없으면 빈 목록
	private List<CardShareResponse> shares(Long groupId, Long meetingId) {
		List<RevisionMemberShare> shares = latestShares(meetingId);
		if (shares.isEmpty()) {
			return List.of();
		}
		List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupIdOrderByIdAsc(groupId);
		Map<Long, Integer> joinOrder = new HashMap<>();
		Map<Long, GroupMember> byMemberId = new HashMap<>();
		for (int i = 0; i < groupMembers.size(); i++) {
			Long id = groupMembers.get(i).getMember().getId();
			joinOrder.put(id, i);
			byMemberId.put(id, groupMembers.get(i));
		}
		return shares.stream()
				.sorted(Comparator.comparingLong(RevisionMemberShare::getShareAmount).reversed()
						.thenComparing(share -> joinOrder.getOrDefault(share.getMember().getId(), Integer.MAX_VALUE)))
				.map(share -> new CardShareResponse(HistoryMemberResponse.of(share.getMember().getId(),
						byMemberId.get(share.getMember().getId())), share.getShareAmount()))
				.toList();
	}

	private List<RevisionMemberShare> latestShares(Long meetingId) {
		return settlementRepository.findByMeetingId(meetingId)
				.filter(Settlement::hasBeenConfirmed)
				.flatMap(s -> settlementRevisionRepository.findBySettlementIdAndRevisionNo(s.getId(),
						s.getCurrentRevisionNo()))
				.map(revision -> revisionMemberShareRepository.findAllByRevisionIdOrderByMemberIdAsc(revision.getId()))
				.orElse(List.of());
	}
}
