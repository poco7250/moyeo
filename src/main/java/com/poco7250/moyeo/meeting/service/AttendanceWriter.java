package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;
import com.poco7250.moyeo.meeting.dto.RoundAttendanceItem;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.dto.AttendanceChange;
import com.poco7250.moyeo.settlement.service.AttendanceSettlementHandler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참석 응답 저장(한 트랜잭션). 동시 첫 응답 재시도는 AttendanceService가 이 메서드를 다시 불러서 한다.
 */
@Component
@RequiredArgsConstructor
public class AttendanceWriter {

	private final MeetingAccessChecker meetingAccessChecker;
	private final GroupMemberRepository groupMemberRepository;
	private final RoundRepository roundRepository;
	private final AttendanceRepository attendanceRepository;
	private final MemberRepository memberRepository;
	private final AttendanceSettlementHandler attendanceSettlementHandler;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * 요청자 본인의 응답을 저장한다. 마감 뒤에는 호스트만 바꿀 수 있다.
	 *
	 * @throws BusinessException 비멤버 NOT_GROUP_MEMBER, 마감 후 비호스트 ATTENDANCE_DEADLINE_PASSED
	 */
	@Transactional
	public MemberAttendanceResponse respondMine(Long meetingId, Long memberId, AttendanceRespondRequest request) {
		Meeting meeting = meetingAccessChecker.requireMember(meetingId, memberId);
		LocalDateTime now = LocalDateTime.now(clock);
		if (meeting.isResponseClosed(now) && !meeting.isHost(memberId)) {
			throw new BusinessException(ErrorCode.ATTENDANCE_DEADLINE_PASSED);
		}
		return save(meeting, memberId, memberId, request, now);
	}

	/**
	 * 호스트가 다른 멤버의 응답을 대신 저장한다(마감과 무관, PRD M-4).
	 *
	 * @throws BusinessException 호스트 아님 NOT_MEETING_HOST, 대상이 그룹 멤버 아님 ATTENDANCE_TARGET_NOT_MEMBER
	 */
	@Transactional
	public MemberAttendanceResponse respondFor(Long meetingId, Long hostId, Long targetMemberId,
			AttendanceRespondRequest request) {
		Meeting meeting = meetingAccessChecker.requireHost(meetingId, hostId);
		if (!groupMemberRepository.existsByGroupIdAndMemberId(meeting.getGroup().getId(), targetMemberId)) {
			throw new BusinessException(ErrorCode.ATTENDANCE_TARGET_NOT_MEMBER);
		}
		return save(meeting, hostId, targetMemberId, request, LocalDateTime.now(clock));
	}

	/**
	 * 요청을 차수별 상태로 풀어 기존 행은 고치고 없는 행은 만든다. 실제로 바뀐 응답이 있을 때만 정산에 반영하고 변경 이벤트를 발행한다.
	 */
	private MemberAttendanceResponse save(Meeting meeting, Long actorId, Long memberId,
			AttendanceRespondRequest request, LocalDateTime now) {
		List<Round> rounds = roundRepository.findAllByMeetingIdOrderBySeq(meeting.getId());
		Map<Round, AttendanceStatus> answers = resolveAnswers(rounds, request);
		Map<Long, Attendance> existing = attendanceRepository
				.findAllByMeetingIdAndMemberId(meeting.getId(), memberId).stream()
				.collect(Collectors.toMap(a -> a.getRound().getId(), Function.identity()));

		List<AttendanceChange> changes = new ArrayList<>();
		answers.forEach((round, status) -> upsert(existing.get(round.getId()), round, memberId, status, now)
				.ifPresent(changes::add));

		MemberAttendanceResponse response = toResponse(memberId, rounds, answers, existing);
		if (!changes.isEmpty()) {
			attendanceSettlementHandler.onChanged(meeting.getId(), actorId, changes);
			eventPublisher.publishEvent(new AttendanceChangedEvent(meeting.getId(), response));
		}
		return response;
	}

	/**
	 * 단축 옵션은 전 차수로 펼치고, 차수별 응답은 모임의 차수인지 확인해 차수 순서대로 담는다.
	 *
	 * @throws BusinessException 모임에 없는 차수 ROUND_NOT_FOUND
	 */
	private Map<Round, AttendanceStatus> resolveAnswers(List<Round> rounds, AttendanceRespondRequest request) {
		Map<Round, AttendanceStatus> answers = new LinkedHashMap<>();
		if (request.shortcut() != null) {
			List<AttendanceStatus> statuses = request.shortcut().expand(rounds.size());
			for (int i = 0; i < rounds.size(); i++) {
				answers.put(rounds.get(i), statuses.get(i));
			}
			return answers;
		}
		Map<Long, AttendanceStatus> requested = request.rounds().stream()
				.collect(Collectors.toMap(RoundAttendanceItem::roundId, RoundAttendanceItem::status));
		rounds.stream().filter(r -> requested.containsKey(r.getId()))
				.forEach(r -> answers.put(r, requested.get(r.getId())));
		if (answers.size() != requested.size()) {
			throw new BusinessException(ErrorCode.ROUND_NOT_FOUND);
		}
		return answers;
	}

	/**
	 * 응답 한 건을 반영한다. 기존 값과 같으면 쓰지 않는다(재시도·중복 요청 멱등).
	 *
	 * @return 실제로 바뀐 내용. 같은 값이면 비어 있다
	 */
	private Optional<AttendanceChange> upsert(Attendance current, Round round, Long memberId, AttendanceStatus status,
			LocalDateTime now) {
		if (current == null) {
			Attendance created = attendanceRepository.save(
					Attendance.respond(round, memberRepository.getReferenceById(memberId), status, now));
			return Optional.of(new AttendanceChange(created.getId(), round.getId(), memberId, null, status));
		}
		AttendanceStatus before = current.getStatus();
		if (before == status) {
			return Optional.empty();
		}
		current.changeStatus(status, now);
		return Optional.of(new AttendanceChange(current.getId(), round.getId(), memberId, before, status));
	}

	/**
	 * 이번에 바뀐 응답과 이전부터 있던 응답을 합쳐 차수 순서대로 돌려준다(응답 안 한 차수는 뺀다).
	 */
	private MemberAttendanceResponse toResponse(Long memberId, List<Round> rounds,
			Map<Round, AttendanceStatus> answers, Map<Long, Attendance> existing) {
		List<RoundAttendanceItem> items = rounds.stream()
				.filter(r -> answers.containsKey(r) || existing.containsKey(r.getId()))
				.map(r -> new RoundAttendanceItem(r.getId(),
						answers.containsKey(r) ? answers.get(r) : existing.get(r.getId()).getStatus()))
				.toList();
		return new MemberAttendanceResponse(memberId, items);
	}
}
