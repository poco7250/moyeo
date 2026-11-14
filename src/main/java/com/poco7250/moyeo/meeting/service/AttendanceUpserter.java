package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.dto.AttendanceChange;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 차수 응답 한 건을 반영한다(참석 응답·실제 참석자 확정 공용). 호출하는 쪽 트랜잭션 안에서 쓴다.
 */
@Component
@RequiredArgsConstructor
class AttendanceUpserter {

	private final AttendanceRepository attendanceRepository;
	private final MemberRepository memberRepository;

	/**
	 * 기존 행은 고치고 없으면 만든다. 기존 값과 같으면 쓰지 않는다(재시도·중복 요청 멱등).
	 *
	 * @return 실제로 바뀐 내용. 같은 값이면 비어 있다
	 */
	Optional<AttendanceChange> upsert(Attendance current, Round round, Long memberId, AttendanceStatus status,
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
}
