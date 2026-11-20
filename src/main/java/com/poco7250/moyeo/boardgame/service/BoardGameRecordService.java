package com.poco7250.moyeo.boardgame.service;

import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordDeletedResponse;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordResponse;
import com.poco7250.moyeo.boardgame.repository.BoardGameRecordRepository;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보드게임 승패 기록(PRD R-7)의 생성·조회·수정·삭제. 벌칙·정산과 연결하지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BoardGameRecordService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final BoardGameRecordRepository boardGameRecordRepository;
	private final AttendanceRepository attendanceRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 차수 참석자 중 고른 회원의 순위로 보드게임 기록을 만든다. 만든 회원이 기록한 사람이 된다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 참석자가 아닌 회원이 있으면 GAME_PARTICIPANT_NOT_ATTENDING
	 */
	public BoardGameRecordResponse create(Long roundId, Long memberId, BoardGameRecordRequest request) {
		Round round = meetingAccessChecker.requireRoundMember(roundId, memberId);
		requireAttending(roundId, request.memberIds());
		BoardGameRecord record = boardGameRecordRepository.saveAndFlush(
				BoardGameRecord.create(round, request.gameName(), memberId, request.toRanks(),
						request.loserMemberId()));
		return publishChanged(round.getMeeting().getId(), record);
	}

	/**
	 * 차수의 보드게임 기록을 최근에 만든 순으로 돌려준다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면(없는 차수 포함) NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public List<BoardGameRecordResponse> list(Long roundId, Long memberId) {
		meetingAccessChecker.requireRoundMember(roundId, memberId);
		return boardGameRecordRepository.findAllByRoundIdOrderByIdDesc(roundId).stream()
				.map(BoardGameRecordResponse::from)
				.toList();
	}

	/**
	 * 게임명과 순위 목록을 통째로 바꾼다. 새 참가자도 지금의 차수 참석자여야 한다.
	 *
	 * @throws BusinessException 없는 기록·비멤버면 NOT_GROUP_MEMBER, 기록한 사람·호스트가 아니면 NOT_BOARD_GAME_RECORDER,
	 *         참석자가 아닌 회원이 있으면 GAME_PARTICIPANT_NOT_ATTENDING
	 */
	public BoardGameRecordResponse update(Long recordId, Long memberId, BoardGameRecordRequest request) {
		BoardGameRecord record = requireEditable(recordId, memberId);
		requireAttending(record.getRound().getId(), request.memberIds());
		record.update(request.gameName(), request.toRanks(), request.loserMemberId());
		// 낙관적 락 충돌과 수정 시각을 이 요청 안에서 확정한다
		boardGameRecordRepository.flush();
		return publishChanged(record.getRound().getMeeting().getId(), record);
	}

	/**
	 * 보드게임 기록을 지운다.
	 *
	 * @throws BusinessException 없는 기록·비멤버면 NOT_GROUP_MEMBER, 기록한 사람·호스트가 아니면 NOT_BOARD_GAME_RECORDER
	 */
	public void delete(Long recordId, Long memberId) {
		BoardGameRecord record = requireEditable(recordId, memberId);
		Long meetingId = record.getRound().getMeeting().getId();
		BoardGameRecordDeletedResponse deleted = new BoardGameRecordDeletedResponse(record.getId(),
				record.getRound().getId());
		boardGameRecordRepository.delete(record);
		boardGameRecordRepository.flush();
		eventPublisher.publishEvent(new BoardGameRecordDeletedEvent(meetingId, deleted));
	}

	/**
	 * 기록을 읽고 회원이 고치거나 지울 수 있는지 검사한다. 없는 기록도 403으로 응답해 존재 여부를 드러내지 않는다.
	 */
	private BoardGameRecord requireEditable(Long recordId, Long memberId) {
		BoardGameRecord record = boardGameRecordRepository.findById(recordId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
		Meeting meeting = meetingAccessChecker.requireMember(record.getRound().getMeeting().getId(), memberId);
		if (!record.isEditableBy(memberId, meetingAccessChecker.hasHostAuthority(meeting, memberId))) {
			throw new BusinessException(ErrorCode.NOT_BOARD_GAME_RECORDER);
		}
		return record;
	}

	private void requireAttending(Long roundId, List<Long> memberIds) {
		if (attendanceRepository.findAttendingMembers(roundId, memberIds).size() != memberIds.size()) {
			throw new BusinessException(ErrorCode.GAME_PARTICIPANT_NOT_ATTENDING);
		}
	}

	private BoardGameRecordResponse publishChanged(Long meetingId, BoardGameRecord record) {
		BoardGameRecordResponse response = BoardGameRecordResponse.from(record);
		eventPublisher.publishEvent(new BoardGameRecordChangedEvent(meetingId, response));
		return response;
	}
}
