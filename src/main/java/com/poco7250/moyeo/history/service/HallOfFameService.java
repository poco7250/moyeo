package com.poco7250.moyeo.history.service;

import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.boardgame.repository.BoardGameRecordRepository;
import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.history.dto.HallOfFameResponse;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.dto.MemberPenaltyAmount;
import com.poco7250.moyeo.settlement.repository.PenaltyChargeRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 명예의 전당 조회(PRD H-2). 기간 안의 지난 모임을 대상으로 모임 수와 상관없이 고정된 수의 쿼리로 읽는다.
 * 읽기 전용이라 쓰기·실시간 이벤트가 없다.
 */
@Service
@RequiredArgsConstructor
public class HallOfFameService {

	private final GroupAccessChecker groupAccessChecker;
	private final GroupMemberRepository groupMemberRepository;
	private final MeetingRepository meetingRepository;
	private final RoundRepository roundRepository;
	private final AttendanceRepository attendanceRepository;
	private final PenaltyRepository penaltyRepository;
	private final PenaltyChargeRepository penaltyChargeRepository;
	private final BoardGameRecordRepository boardGameRecordRepository;
	private final Clock clock;

	/**
	 * 그룹의 명예의 전당을 돌려준다. 대상은 시작 시각이 지금 이전인 모임(날짜 투표 중 제외)이고,
	 * 연도를 주면 그해에 시작한 모임만 센다.
	 *
	 * @param year 집계 연도(서울 시간 기준), 전체 기간이면 null
	 * @throws BusinessException 그룹 멤버가 아니면(없는 그룹 포함) NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public HallOfFameResponse get(Long groupId, Long memberId, Integer year) {
		groupAccessChecker.requireMember(groupId, memberId);
		List<GroupMember> groupMembers = groupMemberRepository.findAllByGroupIdOrderByIdAsc(groupId);
		HistoryPeriod period = HistoryPeriod.of(year, clock);
		List<Meeting> meetings = meetingRepository.findAllByGroupIdAndStartAtInRange(groupId, period.from(),
				period.to());
		HallOfFameSource source = meetings.isEmpty() ? HallOfFameSource.empty(groupMembers)
				: load(meetings, groupMembers);
		return new HallOfFameAssembler(source).assemble(year);
	}

	private HallOfFameSource load(List<Meeting> meetings, List<GroupMember> groupMembers) {
		List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
		List<Round> rounds = roundRepository.findAllByMeetingIdIn(meetingIds);
		List<Long> roundIds = rounds.stream().map(Round::getId).toList();
		if (roundIds.isEmpty()) {
			return new HallOfFameSource(meetings, rounds, List.of(), List.of(), List.of(), Map.of(), List.of(),
					groupMembers);
		}
		List<Attendance> attendances = attendanceRepository.findAllAttendingByRoundIdIn(roundIds);
		List<Attendance> noShows = attendanceRepository.findAllNoShowByRoundIdIn(roundIds);
		List<Penalty> penalties = penaltyRepository.findAllActiveByMeetingIdIn(meetingIds);
		List<BoardGameRecord> boardGames = boardGameRecordRepository.findAllByRoundIdInOrderByIdAsc(roundIds);
		return new HallOfFameSource(meetings, rounds, attendances, noShows, penalties, penaltyAmounts(meetingIds),
				boardGames, groupMembers);
	}

	private Map<Long, Long> penaltyAmounts(List<Long> meetingIds) {
		return penaltyChargeRepository.sumLatestAmountByMemberAndMeetingIdIn(meetingIds).stream()
				.collect(Collectors.toMap(MemberPenaltyAmount::memberId, MemberPenaltyAmount::totalAmount));
	}
}
