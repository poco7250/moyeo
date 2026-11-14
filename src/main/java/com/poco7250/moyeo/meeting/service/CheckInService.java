package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.concurrency.OptimisticRetryExecutor;
import com.poco7250.moyeo.meeting.dto.AttendanceConfirmResponse;
import com.poco7250.moyeo.meeting.dto.CheckInResponse;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 당일 체크인·실제 참석자 확정(PRD M-7). 응답 행이 없는 멤버의 행을 응답과 동시에 만들면 (round, member) 유니크 위반이 나서
 * 새 트랜잭션으로 한 번 더 시도한다. 확정은 지출 있는 차수면 정산 직렬화 대상이라 낙관적 락 충돌도 다시 시도한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

	private final CheckInWriter checkInWriter;
	private final OptimisticRetryExecutor retryExecutor;

	/**
	 * 체크인 여부를 바꾼다(본인, 또는 호스트가 다른 멤버를).
	 */
	public CheckInResponse changeCheckIn(Long roundId, Long actorId, Long targetMemberId, boolean checkedIn) {
		return retryExecutor.execute(() -> withUniqueRetry(
				() -> checkInWriter.changeCheckIn(roundId, actorId, targetMemberId, checkedIn)));
	}

	/**
	 * 차수의 실제 참석자를 확정한다(호스트).
	 */
	public AttendanceConfirmResponse confirm(Long roundId, Long hostId) {
		return retryExecutor.execute(() -> withUniqueRetry(() -> checkInWriter.confirm(roundId, hostId)));
	}

	private <T> T withUniqueRetry(Supplier<T> action) {
		try {
			return action.get();
		} catch (DataIntegrityViolationException e) {
			log.info("체크인 동시 행 생성 감지, 재시도: {}", e.getMostSpecificCause().getClass().getSimpleName());
			return action.get();
		}
	}
}
