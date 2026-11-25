package com.poco7250.moyeo.history.service;

import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.history.dto.YearReviewResponse;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.dto.MeetingExpenseTotal;
import com.poco7250.moyeo.settlement.dto.MemberPenaltyAmount;
import com.poco7250.moyeo.settlement.dto.MemberShareAmount;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.PenaltyChargeRepository;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연말 결산 조회(PRD H-4). 그해 지난 모임을 대상으로 모임 수와 상관없이 고정된 수의 쿼리로 읽는다.
 * 읽기 전용이라 쓰기·실시간 이벤트가 없다.
 */
@Service
@RequiredArgsConstructor
public class YearReviewService {

	private final GroupAccessChecker groupAccessChecker;
	private final GroupMemberRepository groupMemberRepository;
	private final MeetingRepository meetingRepository;
	private final RoundRepository roundRepository;
	private final AttendanceRepository attendanceRepository;
	private final ExpenseRepository expenseRepository;
	private final SettlementRepository settlementRepository;
	private final PenaltyRepository penaltyRepository;
	private final PenaltyChargeRepository penaltyChargeRepository;
	private final RevisionMemberShareRepository revisionMemberShareRepository;
	private final Clock clock;

	/**
	 * 그룹의 연말 결산을 돌려준다. 대상은 그해(서울 시간)에 시작했고 시작 시각이 지금 이전인 모임(날짜 투표 중 제외)이다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면(없는 그룹 포함) NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public YearReviewResponse get(Long groupId, Long memberId, int year) {
		groupAccessChecker.requireMember(groupId, memberId);
		List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupIdOrderByIdAsc(groupId);
		// 요청자가 멤버라 목록이 비어 있지 않다
		String groupName = groupMembers.getFirst().getGroup().getName();
		HistoryPeriod period = HistoryPeriod.of(year, clock);
		List<Meeting> meetings = meetingRepository.findAllByGroupIdAndStartAtInRange(groupId, period.from(),
				period.to());
		YearReviewSource source = meetings.isEmpty() ? YearReviewSource.empty(groupName, groupMembers)
				: load(groupName, meetings, groupMembers);
		return new YearReviewAssembler(source).assemble(year);
	}

	private YearReviewSource load(String groupName, List<Meeting> meetings, List<GroupMember> groupMembers) {
		List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
		List<Round> rounds = roundRepository.findAllWithConfirmedPlaceByMeetingIdIn(meetingIds);
		List<Long> roundIds = rounds.stream().map(Round::getId).toList();
		List<Attendance> attendances = roundIds.isEmpty() ? List.of()
				: attendanceRepository.findAllAttendingByRoundIdIn(roundIds);
		List<Attendance> noShows = roundIds.isEmpty() ? List.of()
				: attendanceRepository.findAllNoShowByRoundIdIn(roundIds);
		List<Penalty> penalties = roundIds.isEmpty() ? List.of()
				: penaltyRepository.findAllActiveByMeetingIdIn(meetingIds);
		return new YearReviewSource(groupName, meetings, rounds, attendances, noShows, totalExpense(meetingIds),
				confirmedMeetingCount(meetingIds), penalties, penaltyAmounts(meetingIds), shareAmounts(meetingIds),
				groupMembers);
	}

	private long totalExpense(List<Long> meetingIds) {
		return expenseRepository.sumAmountByMeetingIdIn(meetingIds).stream()
				.mapToLong(MeetingExpenseTotal::totalAmount).sum();
	}

	private long confirmedMeetingCount(List<Long> meetingIds) {
		return settlementRepository.findAllByMeetingIdIn(meetingIds).stream()
				.filter(Settlement::hasBeenConfirmed).count();
	}

	private Map<Long, Long> penaltyAmounts(List<Long> meetingIds) {
		return penaltyChargeRepository.sumLatestAmountByMemberAndMeetingIdIn(meetingIds).stream()
				.collect(Collectors.toMap(MemberPenaltyAmount::memberId, MemberPenaltyAmount::totalAmount));
	}

	private Map<Long, Long> shareAmounts(List<Long> meetingIds) {
		return revisionMemberShareRepository.sumLatestShareByMemberAndMeetingIdIn(meetingIds).stream()
				.collect(Collectors.toMap(MemberShareAmount::memberId, MemberShareAmount::totalAmount));
	}
}
