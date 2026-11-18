package com.poco7250.moyeo.boardgame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.boardgame.domain.BoardGameRank;
import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.boardgame.dto.BoardGameRankRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRankResponse;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordResponse;
import com.poco7250.moyeo.boardgame.repository.BoardGameRecordRepository;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.member.domain.Member;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BoardGameRecordServiceTest {

	private static final Long MEETING_ID = 10L;
	private static final Long ROUND_ID = 100L;
	private static final Long RECORD_ID = 300L;
	private static final Long HOST_ID = 7L;
	private static final Long RECORDER_ID = 8L;
	private static final Long OTHER_ID = 9L;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	BoardGameRecordRepository boardGameRecordRepository;

	@Mock
	AttendanceRepository attendanceRepository;

	@Mock
	ApplicationEventPublisher eventPublisher;

	private BoardGameRecordService service;
	private Meeting meeting;
	private Round round;

	@BeforeEach
	void setUp() {
		service = new BoardGameRecordService(meetingAccessChecker, boardGameRecordRepository, attendanceRepository,
				eventPublisher);
		meeting = Meeting.create(Group.create("대학 동기"), "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null,
				null, HOST_ID);
		ReflectionTestUtils.setField(meeting, "id", MEETING_ID);
		round = Round.create(meeting, 2, "보드게임");
		ReflectionTestUtils.setField(round, "id", ROUND_ID);
	}

	private static BoardGameRecordRequest request(String gameName) {
		return new BoardGameRecordRequest(gameName,
				List.of(new BoardGameRankRequest(OTHER_ID, 2), new BoardGameRankRequest(RECORDER_ID, 1)));
	}

	private void givenAttending(int count) {
		given(attendanceRepository.findAttendingMembers(anyLong(), anyList()))
				.willReturn(Collections.nCopies(count, Member.ofKakao(1L, "회원", null, null)));
	}

	private BoardGameRecord givenSavedRecord() {
		BoardGameRecord record = BoardGameRecord.create(round, "스플렌더", RECORDER_ID,
				List.of(BoardGameRank.of(RECORDER_ID, 1), BoardGameRank.of(OTHER_ID, 2)));
		ReflectionTestUtils.setField(record, "id", RECORD_ID);
		given(boardGameRecordRepository.findById(RECORD_ID)).willReturn(Optional.of(record));
		given(meetingAccessChecker.requireMember(eq(MEETING_ID), anyLong())).willReturn(meeting);
		return record;
	}

	@Test
	@DisplayName("참석자 순위로 기록을 만들고 정렬된 순위와 BOARD_GAME_RECORD_CHANGED를 발행한다")
	void create() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, RECORDER_ID)).willReturn(round);
		givenAttending(2);
		given(boardGameRecordRepository.saveAndFlush(any(BoardGameRecord.class))).willAnswer(invocation -> {
			BoardGameRecord saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", RECORD_ID);
			return saved;
		});

		BoardGameRecordResponse response = service.create(ROUND_ID, RECORDER_ID, request("스플렌더"));

		assertThat(response.id()).isEqualTo(RECORD_ID);
		assertThat(response.roundId()).isEqualTo(ROUND_ID);
		assertThat(response.recordedByMemberId()).isEqualTo(RECORDER_ID);
		assertThat(response.ranks()).containsExactly(new BoardGameRankResponse(RECORDER_ID, 1),
				new BoardGameRankResponse(OTHER_ID, 2));
		ArgumentCaptor<BoardGameRecordChangedEvent> event = ArgumentCaptor.forClass(BoardGameRecordChangedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().destination()).isEqualTo(RealtimeTopics.meeting(MEETING_ID));
		assertThat(event.getValue().data()).isEqualTo(response);
	}

	@Test
	@DisplayName("차수 참석자가 아닌 회원이 있으면 400 GAME_PARTICIPANT_NOT_ATTENDING이고 저장하지 않는다")
	void createRejectsNonAttendee() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, RECORDER_ID)).willReturn(round);
		givenAttending(1);

		assertThatThrownBy(() -> service.create(ROUND_ID, RECORDER_ID, request("스플렌더")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.GAME_PARTICIPANT_NOT_ATTENDING);
		verify(boardGameRecordRepository, never()).saveAndFlush(any());
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("기록한 사람이 수정하면 게임명·순위가 바뀌고 flush 후 이벤트를 발행한다")
	void updateByRecorder() {
		BoardGameRecord record = givenSavedRecord();
		givenAttending(2);

		BoardGameRecordResponse response = service.update(RECORD_ID, RECORDER_ID, request("카탄"));

		assertThat(response.gameName()).isEqualTo("카탄");
		assertThat(record.getGameName()).isEqualTo("카탄");
		verify(boardGameRecordRepository).flush();
		verify(eventPublisher).publishEvent(any(BoardGameRecordChangedEvent.class));
	}

	@Test
	@DisplayName("호스트는 남이 만든 기록도 지울 수 있고 BOARD_GAME_RECORD_DELETED를 발행한다")
	void deleteByHost() {
		BoardGameRecord record = givenSavedRecord();

		service.delete(RECORD_ID, HOST_ID);

		verify(boardGameRecordRepository).delete(record);
		ArgumentCaptor<BoardGameRecordDeletedEvent> event = ArgumentCaptor.forClass(BoardGameRecordDeletedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().data().id()).isEqualTo(RECORD_ID);
		assertThat(event.getValue().data().roundId()).isEqualTo(ROUND_ID);
	}

	@Test
	@DisplayName("기록한 사람·호스트가 아닌 멤버의 수정·삭제는 403 NOT_BOARD_GAME_RECORDER이고 아무것도 바뀌지 않는다")
	void rejectsOtherMember() {
		BoardGameRecord record = givenSavedRecord();

		assertThatThrownBy(() -> service.update(RECORD_ID, OTHER_ID, request("카탄")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_BOARD_GAME_RECORDER);
		assertThatThrownBy(() -> service.delete(RECORD_ID, OTHER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_BOARD_GAME_RECORDER);
		assertThat(record.getGameName()).isEqualTo("스플렌더");
		verify(boardGameRecordRepository, never()).delete(any());
		verifyNoInteractions(attendanceRepository, eventPublisher);
	}

	@Test
	@DisplayName("없는 기록은 403 NOT_GROUP_MEMBER")
	void missingRecord() {
		given(boardGameRecordRepository.findById(RECORD_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(RECORD_ID, RECORDER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(meetingAccessChecker, eventPublisher);
	}
}
