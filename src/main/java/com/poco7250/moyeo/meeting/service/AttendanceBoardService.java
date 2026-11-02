package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceBoardResponse;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.RoundBoard;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참석 현황판(PRD M-4). 차수·응답·그룹 멤버를 한 번씩 읽어 메모리에서 나눈다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceBoardService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final RoundRepository roundRepository;
	private final AttendanceRepository attendanceRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final Clock clock;

	/**
	 * 차수별 참석/불참/미정/미응답 멤버 목록을 돌려준다. 미응답 = 그룹 멤버 중 그 차수에 응답이 없는 사람.
	 *
	 * @throws BusinessException 없는 모임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public AttendanceBoardResponse getBoard(Long meetingId, Long memberId) {
		Meeting meeting = meetingAccessChecker.requireMember(meetingId, memberId);
		List<Round> rounds = roundRepository.findAllByMeetingIdOrderBySeq(meetingId);
		List<GroupMember> members = groupMemberRepository.findAllByGroupIdOrderByIdAsc(meeting.getGroup().getId());
		Map<Long, Map<Long, AttendanceStatus>> statusByRound = groupByRound(
				attendanceRepository.findAllByMeetingId(meetingId));

		List<RoundBoard> boards = rounds.stream()
				.map(r -> toRoundBoard(r, members, statusByRound.getOrDefault(r.getId(), Map.of())))
				.toList();
		return new AttendanceBoardResponse(meeting.getResponseDeadline(),
				meeting.isResponseClosed(LocalDateTime.now(clock)), boards);
	}

	/**
	 * 응답을 차수 id → (회원 id → 상태)로 묶는다.
	 */
	private Map<Long, Map<Long, AttendanceStatus>> groupByRound(List<Attendance> attendances) {
		Map<Long, Map<Long, AttendanceStatus>> result = new HashMap<>();
		for (Attendance a : attendances) {
			result.computeIfAbsent(a.getRound().getId(), k -> new HashMap<>())
					.put(a.getMember().getId(), a.getStatus());
		}
		return result;
	}

	/**
	 * 그룹 멤버를 가입 순서 그대로 응답 상태별로 나눈다. 응답이 없으면 미응답이다.
	 */
	static RoundBoard toRoundBoard(Round round, List<GroupMember> members, Map<Long, AttendanceStatus> statuses) {
		Map<AttendanceStatus, List<BoardMember>> byStatus = members.stream()
				.filter(m -> statuses.containsKey(m.getMember().getId()))
				.collect(Collectors.groupingBy(m -> statuses.get(m.getMember().getId()),
						Collectors.mapping(BoardMember::from, Collectors.toList())));
		List<BoardMember> noResponse = members.stream()
				.filter(m -> !statuses.containsKey(m.getMember().getId()))
				.map(BoardMember::from)
				.toList();
		return new RoundBoard(round.getId(), round.getSeq(), round.getName(),
				byStatus.getOrDefault(AttendanceStatus.ATTEND, List.of()),
				byStatus.getOrDefault(AttendanceStatus.ABSENT, List.of()),
				byStatus.getOrDefault(AttendanceStatus.UNDECIDED, List.of()),
				noResponse);
	}
}
