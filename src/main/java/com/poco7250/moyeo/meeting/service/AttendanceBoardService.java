package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.config.MeetingLinkProperties;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.MeetingShareText;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceBoardResponse;
import com.poco7250.moyeo.meeting.dto.AttendanceReminderResponse;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.RoundBoard;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참석 현황판(PRD M-4)과 미응답자 리마인드(M-6). 차수·응답·그룹 멤버를 한 번씩 읽어 메모리에서 나눈다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceBoardService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final RoundRepository roundRepository;
	private final AttendanceRepository attendanceRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final Clock clock;
	private final MeetingLinkProperties meetingLinkProperties;

	/**
	 * 차수별 참석/불참/미정/미응답 멤버 목록을 돌려준다. 미응답 = 그룹 멤버 중 그 차수에 응답이 없는 사람.
	 *
	 * @throws BusinessException 없는 모임이거나 그룹 멤버가 아니면 NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public AttendanceBoardResponse getBoard(Long meetingId, Long memberId) {
		Meeting meeting = meetingAccessChecker.requireMember(meetingId, memberId);
		return new AttendanceBoardResponse(meeting.getResponseDeadline(),
				meeting.isResponseClosed(LocalDateTime.now(clock)), roundBoards(meeting, groupMembers(meeting)));
	}

	/**
	 * 한 차수라도 응답하지 않은 멤버와 단톡방용 리마인드 문구를 돌려준다(PRD M-6). 아무것도 저장하지 않는다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 응답 마감이 지났으면 ATTENDANCE_REMINDER_CLOSED
	 */
	@Transactional(readOnly = true)
	public AttendanceReminderResponse getReminder(Long meetingId, Long memberId) {
		Meeting meeting = meetingAccessChecker.requireMember(meetingId, memberId);
		if (meeting.isResponseClosed(LocalDateTime.now(clock))) {
			throw new BusinessException(ErrorCode.ATTENDANCE_REMINDER_CLOSED);
		}
		List<GroupMember> members = groupMembers(meeting);
		List<BoardMember> noResponse = unionNoResponse(members, roundBoards(meeting, members));
		String shareText = noResponse.isEmpty() ? null
				: MeetingShareText.reminderText(meeting.getTitle(), meeting.getStartAt(),
						noResponse.stream().map(BoardMember::nickname).toList(), meeting.getResponseDeadline(),
						meetingLinkProperties.urlOf(meetingId));
		return new AttendanceReminderResponse(meetingId, meeting.getResponseDeadline(), noResponse, shareText);
	}

	/**
	 * 차수·응답·그룹 멤버를 한 번씩 읽어 차수별 현황으로 나눈다.
	 */
	private List<RoundBoard> roundBoards(Meeting meeting, List<GroupMember> members) {
		List<Round> rounds = roundRepository.findAllByMeetingIdOrderBySeq(meeting.getId());
		Map<Long, Map<Long, Attendance>> byRound = groupByRound(attendanceRepository.findAllByMeetingId(meeting.getId()));
		return rounds.stream()
				.map(r -> toRoundBoard(r, members, byRound.getOrDefault(r.getId(), Map.of())))
				.toList();
	}

	/**
	 * 모임이 속한 그룹의 멤버를 가입 순으로 읽는다.
	 */
	private List<GroupMember> groupMembers(Meeting meeting) {
		return groupMemberRepository.findAllByGroupIdOrderByIdAsc(meeting.getGroup().getId());
	}

	/**
	 * 한 차수라도 미응답인 멤버를 그룹 가입 순으로 중복 없이 모은다.
	 */
	static List<BoardMember> unionNoResponse(List<GroupMember> members, List<RoundBoard> boards) {
		Set<Long> noResponseIds = boards.stream()
				.flatMap(b -> b.noResponse().stream())
				.map(BoardMember::memberId)
				.collect(Collectors.toSet());
		return members.stream()
				.filter(m -> noResponseIds.contains(m.getMember().getId()))
				.map(BoardMember::from)
				.toList();
	}

	/**
	 * 응답을 차수 id → (회원 id → 응답)으로 묶는다.
	 */
	private Map<Long, Map<Long, Attendance>> groupByRound(List<Attendance> attendances) {
		Map<Long, Map<Long, Attendance>> result = new HashMap<>();
		for (Attendance a : attendances) {
			result.computeIfAbsent(a.getRound().getId(), k -> new HashMap<>()).put(a.getMember().getId(), a);
		}
		return result;
	}

	/**
	 * 그룹 멤버를 가입 순서 그대로 응답 상태별로 나눈다. 응답이 없으면 미응답이다. 체크인한 멤버 id도 가입 순으로 담는다.
	 */
	static RoundBoard toRoundBoard(Round round, List<GroupMember> members, Map<Long, Attendance> responses) {
		Map<AttendanceStatus, List<BoardMember>> byStatus = members.stream()
				.filter(m -> responses.containsKey(m.getMember().getId()))
				.collect(Collectors.groupingBy(m -> responses.get(m.getMember().getId()).getStatus(),
						Collectors.mapping(BoardMember::from, Collectors.toList())));
		List<BoardMember> noResponse = members.stream()
				.filter(m -> !responses.containsKey(m.getMember().getId()))
				.map(BoardMember::from)
				.toList();
		List<Long> checkedIn = members.stream()
				.map(m -> m.getMember().getId())
				.filter(id -> responses.containsKey(id) && responses.get(id).isCheckedIn())
				.toList();
		return new RoundBoard(round.getId(), round.getSeq(), round.getName(),
				byStatus.getOrDefault(AttendanceStatus.ATTEND, List.of()),
				byStatus.getOrDefault(AttendanceStatus.ABSENT, List.of()),
				byStatus.getOrDefault(AttendanceStatus.UNDECIDED, List.of()),
				noResponse, checkedIn, round.getAttendanceConfirmedAt());
	}
}
