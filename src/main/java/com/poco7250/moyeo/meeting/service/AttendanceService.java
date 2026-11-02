package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.meeting.dto.AttendanceRespondRequest;
import com.poco7250.moyeo.meeting.dto.MemberAttendanceResponse;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 차수별 참석 응답(PRD M-3, M-4). 같은 멤버의 첫 응답이 동시에 들어와 (round, member) 유니크 위반이 나면
 * 새 트랜잭션으로 한 번 더 시도한다(두 번째 시도에서는 기존 행을 고친다).
 * 실패한 트랜잭션 안에서는 재조회할 수 없어서 트랜잭션은 AttendanceWriter가 호출마다 따로 연다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

	private final AttendanceWriter attendanceWriter;

	/**
	 * 내 참석 응답을 저장한다.
	 */
	public MemberAttendanceResponse respondMine(Long meetingId, Long memberId, AttendanceRespondRequest request) {
		return withRetry(() -> attendanceWriter.respondMine(meetingId, memberId, request));
	}

	/**
	 * 호스트가 다른 멤버의 참석 응답을 대신 저장한다.
	 */
	public MemberAttendanceResponse respondFor(Long meetingId, Long hostId, Long targetMemberId,
			AttendanceRespondRequest request) {
		return withRetry(() -> attendanceWriter.respondFor(meetingId, hostId, targetMemberId, request));
	}

	private MemberAttendanceResponse withRetry(Supplier<MemberAttendanceResponse> action) {
		try {
			return action.get();
		} catch (DataIntegrityViolationException e) {
			log.info("참석 동시 응답 감지, 재시도: {}", e.getMostSpecificCause().getClass().getSimpleName());
			return action.get();
		}
	}
}
