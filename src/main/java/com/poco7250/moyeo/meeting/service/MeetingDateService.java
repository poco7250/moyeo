package com.poco7250.moyeo.meeting.service;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.dto.MeetingDateVotesResponse;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 날짜 후보 투표(PRD M-5) facade. 쓰기는 MeetingDateWriter 트랜잭션으로 하고,
 * 바뀐 현황은 커밋 뒤 MeetingDateTally가 새 트랜잭션에서 읽어 DATE_VOTE_CHANGED로 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingDateService {

	private final MeetingDateWriter meetingDateWriter;
	private final MeetingDateTally meetingDateTally;
	private final MeetingService meetingService;

	/**
	 * 호스트가 날짜 후보를 추가하고 바뀐 현황을 돌려준다.
	 * 같은 시각이 동시에 들어와 유니크 위반이 나면 중복 후보로 거부한다.
	 */
	public MeetingDateVotesResponse addCandidate(Long meetingId, Long hostId, LocalDateTime startAt) {
		try {
			meetingDateWriter.addCandidate(meetingId, hostId, startAt);
		} catch (DataIntegrityViolationException e) {
			log.info("날짜 후보 동시 추가 감지: {}", e.getMostSpecificCause().getClass().getSimpleName());
			throw new BusinessException(ErrorCode.MEETING_DATE_CANDIDATE_DUPLICATED);
		}
		return meetingDateTally.publish(meetingId);
	}

	/**
	 * 내 가능 후보를 설정하고 현황을 돌려준다. 응답이 실제로 바뀌었을 때만 DATE_VOTE_CHANGED를 발행한다.
	 * 같은 회원의 첫 응답이 동시에 들어와 유니크 위반이 나면 한 번 더 시도한다(두 번째는 기존 행을 고친다).
	 */
	public MeetingDateVotesResponse respond(Long meetingId, Long memberId, List<Long> availableCandidateIds) {
		boolean changed;
		try {
			changed = meetingDateWriter.respond(meetingId, memberId, availableCandidateIds);
		} catch (DataIntegrityViolationException e) {
			log.info("날짜 응답 동시 저장 감지, 재시도: {}", e.getMostSpecificCause().getClass().getSimpleName());
			changed = meetingDateWriter.respond(meetingId, memberId, availableCandidateIds);
		}
		return changed ? meetingDateTally.publish(meetingId) : meetingDateTally.read(meetingId, memberId);
	}

	/**
	 * 날짜 투표 현황을 조회한다.
	 */
	public MeetingDateVotesResponse getVotes(Long meetingId, Long memberId) {
		return meetingDateTally.read(meetingId, memberId);
	}

	/**
	 * 호스트가 날짜를 확정하고, 시작 시각이 채워진 모임 상세를 돌려준다.
	 */
	public MeetingDetailResponse confirm(Long meetingId, Long hostId, Long candidateId) {
		meetingDateWriter.confirm(meetingId, hostId, candidateId);
		return meetingService.getDetail(meetingId, hostId);
	}
}
