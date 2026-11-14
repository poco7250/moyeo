package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceConfirmResponse;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.CheckInResponse;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.dto.AttendanceChange;
import com.poco7250.moyeo.settlement.service.AttendanceSettlementHandler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 당일 체크인과 실제 참석자 확정 저장(PRD M-7, 한 트랜잭션). 재시도는 CheckInService가 이 메서드를 다시 불러서 한다.
 */
@Component
@RequiredArgsConstructor
public class CheckInWriter {

	private final MeetingAccessChecker meetingAccessChecker;
	private final GroupMemberRepository groupMemberRepository;
	private final AttendanceRepository attendanceRepository;
	private final MemberRepository memberRepository;
	private final AttendanceUpserter attendanceUpserter;
	private final AttendanceSettlementHandler attendanceSettlementHandler;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * 체크인 여부를 바꾼다. 본인은 멤버면, 다른 멤버는 호스트만 바꿀 수 있다. 응답 상태는 확정 때까지 그대로다.
	 * 응답 없이 체크인하면 미정(UNDECIDED) 행을 만든다. 같은 값이면 아무것도 하지 않는다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 남의 체크인을 바꾸는 비호스트 NOT_MEETING_HOST,
	 *         대상이 그룹 멤버 아님 ATTENDANCE_TARGET_NOT_MEMBER, 모임 당일 전 CHECK_IN_NOT_OPEN
	 */
	@Transactional
	public CheckInResponse changeCheckIn(Long roundId, Long actorId, Long targetMemberId, boolean checkedIn) {
		Round round = requireActor(roundId, actorId, targetMemberId);
		LocalDateTime now = now();
		round.getMeeting().requireCheckInOpen(now);
		boolean changed = attendanceRepository.findByRoundIdAndMemberId(roundId, targetMemberId)
				.map(attendance -> attendance.changeCheckIn(checkedIn))
				.orElseGet(() -> checkedIn && createCheckedIn(round, targetMemberId, now));
		CheckInResponse response = new CheckInResponse(roundId, targetMemberId, checkedIn);
		if (changed) {
			eventPublisher.publishEvent(new CheckInChangedEvent(round.getMeeting().getId(), response));
		}
		return response;
	}

	/**
	 * 차수의 실제 참석자를 확정한다(호스트). 체크인한 그룹 멤버는 참석, 나머지는 불참으로 응답을 바꾸고(응답 없으면 만든다)
	 * 바뀐 응답은 기존 참석 변경처럼 정산에 반영한다. 다시 확정할 수 있고, 확정 시각은 매번 갱신된다.
	 *
	 * @throws BusinessException 호스트 아님 NOT_MEETING_HOST, 모임 당일 전 CHECK_IN_NOT_OPEN
	 */
	@Transactional
	public AttendanceConfirmResponse confirm(Long roundId, Long hostId) {
		Round round = meetingAccessChecker.requireRoundHost(roundId, hostId);
		Meeting meeting = round.getMeeting();
		LocalDateTime now = now();
		meeting.requireCheckInOpen(now);
		List<GroupMember> members = groupMemberRepository.findAllByGroupIdOrderByIdAsc(meeting.getGroup().getId());
		Map<Long, Attendance> byMember = attendanceRepository.findAllByRoundId(roundId).stream()
				.collect(Collectors.toMap(a -> a.getMember().getId(), Function.identity()));

		List<AttendanceChange> changes = new ArrayList<>();
		members.forEach(m -> attendanceUpserter.upsert(byMember.get(memberIdOf(m)), round, memberIdOf(m),
				confirmedStatus(byMember.get(memberIdOf(m))), now).ifPresent(changes::add));
		round.markAttendanceConfirmed(now);
		if (!changes.isEmpty()) {
			attendanceSettlementHandler.onChanged(meeting.getId(), hostId, changes);
		}
		AttendanceConfirmResponse response = toResponse(meeting.getId(), round, now, members, byMember,
				changes.size());
		eventPublisher.publishEvent(new RoundAttendanceConfirmedEvent(meeting.getId(), response));
		return response;
	}

	/**
	 * 본인 체크인이면 멤버 검사, 남의 체크인이면 호스트 검사와 대상의 그룹 멤버 여부를 본다.
	 */
	private Round requireActor(Long roundId, Long actorId, Long targetMemberId) {
		if (actorId.equals(targetMemberId)) {
			return meetingAccessChecker.requireRoundMember(roundId, actorId);
		}
		Round round = meetingAccessChecker.requireRoundHost(roundId, actorId);
		if (!groupMemberRepository.existsByGroupIdAndMemberId(round.getMeeting().getGroup().getId(),
				targetMemberId)) {
			throw new BusinessException(ErrorCode.ATTENDANCE_TARGET_NOT_MEMBER);
		}
		return round;
	}

	private boolean createCheckedIn(Round round, Long memberId, LocalDateTime now) {
		attendanceRepository.save(
				Attendance.checkInWithoutResponse(round, memberRepository.getReferenceById(memberId), now));
		return true;
	}

	private static AttendanceStatus confirmedStatus(Attendance attendance) {
		return attendance != null && attendance.isCheckedIn() ? AttendanceStatus.ATTEND : AttendanceStatus.ABSENT;
	}

	private static Long memberIdOf(GroupMember member) {
		return member.getMember().getId();
	}

	/**
	 * 확정 결과를 그룹 가입 순으로 참석·불참으로 나눈다.
	 */
	private static AttendanceConfirmResponse toResponse(Long meetingId, Round round, LocalDateTime now,
			List<GroupMember> members, Map<Long, Attendance> byMember, int changedCount) {
		Map<Boolean, List<BoardMember>> split = members.stream().collect(Collectors.partitioningBy(
				m -> confirmedStatus(byMember.get(memberIdOf(m))) == AttendanceStatus.ATTEND,
				Collectors.mapping(BoardMember::from, Collectors.toList())));
		return new AttendanceConfirmResponse(meetingId, round.getId(), now, split.get(true), split.get(false),
				changedCount);
	}

	/**
	 * DB(datetime(6))에 저장되는 정밀도로 맞춘 지금 시각.
	 */
	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
