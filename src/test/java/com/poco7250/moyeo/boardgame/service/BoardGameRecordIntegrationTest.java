package com.poco7250.moyeo.boardgame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.boardgame.dto.BoardGameRankRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRankResponse;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordDeletedResponse;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordRequest;
import com.poco7250.moyeo.boardgame.dto.BoardGameRecordResponse;
import com.poco7250.moyeo.boardgame.repository.BoardGameRecordRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.global.websocket.RealtimeTopics;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보드게임 승패 기록(PRD R-7) 통합 테스트. 실시간 메시지가 커밋 후에 나가는지 보려고 실제로 커밋하고 끝나면 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BoardGameRecordIntegrationTest {

	private static final LocalDateTime RESPONDED_AT = LocalDateTime.of(2099, 12, 1, 12, 0);

	@Autowired
	BoardGameRecordService boardGameRecordService;

	@Autowired
	BoardGameRecordRepository boardGameRecordRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Member host;
	private Member minsu;
	private Member younghee;
	private Member absent;
	private Member outsider;
	private Group group;
	private Meeting meeting;
	private Round round;

	@BeforeEach
	void setUp() {
		host = memberRepository.save(Member.ofKakao(5301L, "호스트", null, null));
		minsu = memberRepository.save(Member.ofKakao(5302L, "민수", null, null));
		younghee = memberRepository.save(Member.ofKakao(5303L, "영희", null, null));
		absent = memberRepository.save(Member.ofKakao(5304L, "불참", null, null));
		outsider = memberRepository.save(Member.ofKakao(5305L, "외부인", null, null));
		group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.save(GroupMember.join(group, host, "호스트", GroupRole.OWNER));
		groupMemberRepository.save(GroupMember.join(group, minsu, "민수", GroupRole.MEMBER));
		groupMemberRepository.save(GroupMember.join(group, younghee, "영희", GroupRole.MEMBER));
		groupMemberRepository.save(GroupMember.join(group, absent, "불참", GroupRole.MEMBER));
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 2, "보드게임"));
		attendanceRepository.saveAll(List.of(respond(host, AttendanceStatus.ATTEND),
				respond(minsu, AttendanceStatus.ATTEND), respond(younghee, AttendanceStatus.ATTEND),
				respond(absent, AttendanceStatus.ABSENT)));
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		boardGameRecordRepository.deleteAll(boardGameRecordRepository.findAllByRoundIdOrderByIdDesc(round.getId()));
		attendanceRepository.deleteAll(attendanceRepository.findAllByRoundId(round.getId()));
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meeting.getId());
		groupMemberRepository.deleteAll(groupMemberRepository.findAllByGroupIdOrderByIdAsc(group.getId()));
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(
				List.of(host.getId(), minsu.getId(), younghee.getId(), absent.getId(), outsider.getId()));
	}

	@Test
	@DisplayName("멤버가 만든 기록을 목록에서 보고, 기록한 사람이 고치고, 호스트가 지우며 단계마다 모임 토픽 메시지가 나간다")
	void lifecycle() {
		BoardGameRecordResponse created = boardGameRecordService.create(round.getId(), minsu.getId(),
				request("스플렌더", rank(younghee, 2), rank(minsu, 1), rank(host, 1)));

		assertThat(created.ranks()).containsExactly(new BoardGameRankResponse(host.getId(), 1),
				new BoardGameRankResponse(minsu.getId(), 1), new BoardGameRankResponse(younghee.getId(), 2));
		assertThat(boardGameRecordService.list(round.getId(), younghee.getId())).containsExactly(created);
		verifyMeetingMessage(BoardGameRecordChangedEvent.TYPE, created);

		BoardGameRecordResponse updated = boardGameRecordService.update(created.id(), minsu.getId(),
				request("카탄", rank(minsu, 2), rank(younghee, 1)));
		assertThat(updated.gameName()).isEqualTo("카탄");
		assertThat(boardGameRecordService.list(round.getId(), host.getId())).containsExactly(updated);
		verifyMeetingMessage(BoardGameRecordChangedEvent.TYPE, updated);

		boardGameRecordService.delete(created.id(), host.getId());
		assertThat(boardGameRecordService.list(round.getId(), host.getId())).isEmpty();
		verifyMeetingMessage(BoardGameRecordDeletedEvent.TYPE,
				new BoardGameRecordDeletedResponse(created.id(), round.getId()));
	}

	@Test
	@DisplayName("차수 참석자가 아닌 회원을 넣으면 400 GAME_PARTICIPANT_NOT_ATTENDING이고 기록·메시지가 없다")
	void rejectsNonAttendee() {
		assertThatThrownBy(() -> boardGameRecordService.create(round.getId(), minsu.getId(),
				request("스플렌더", rank(minsu, 1), rank(absent, 2))))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.GAME_PARTICIPANT_NOT_ATTENDING);

		assertThat(boardGameRecordRepository.findAllByRoundIdOrderByIdDesc(round.getId())).isEmpty();
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	@DisplayName("기록한 사람·호스트가 아닌 멤버는 403 NOT_BOARD_GAME_RECORDER, 비멤버는 403 NOT_GROUP_MEMBER")
	void rejectsUnauthorized() {
		BoardGameRecordResponse created = boardGameRecordService.create(round.getId(), minsu.getId(),
				request("스플렌더", rank(minsu, 1), rank(younghee, 2)));

		assertThatThrownBy(() -> boardGameRecordService.delete(created.id(), younghee.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_BOARD_GAME_RECORDER);
		assertThatThrownBy(() -> boardGameRecordService.update(created.id(), outsider.getId(),
				request("카탄", rank(minsu, 1), rank(younghee, 2))))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		assertThatThrownBy(() -> boardGameRecordService.list(round.getId(), outsider.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		assertThat(boardGameRecordService.list(round.getId(), host.getId())).containsExactly(created);
	}

	@Test
	@DisplayName("읽어 둔 기록이 다른 요청에서 먼저 지워지면 늦은 수정은 낙관적 락 충돌이 된다")
	void staleUpdateConflicts() {
		BoardGameRecordResponse created = boardGameRecordService.create(round.getId(), minsu.getId(),
				request("스플렌더", rank(minsu, 1), rank(younghee, 2)));

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			boardGameRecordRepository.findById(created.id()).orElseThrow();
			// 이 트랜잭션이 기록을 읽어 둔 사이에 호스트의 삭제가 먼저 커밋된다
			CompletableFuture.runAsync(() -> boardGameRecordService.delete(created.id(), host.getId()))
					.orTimeout(30, TimeUnit.SECONDS).join();
			boardGameRecordService.update(created.id(), minsu.getId(), request("카탄", rank(minsu, 1), rank(younghee, 2)));
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(boardGameRecordRepository.findById(created.id())).isEmpty();
	}

	private void verifyMeetingMessage(String type, Object data) {
		verify(messagingTemplate).convertAndSend(eq(RealtimeTopics.meeting(meeting.getId())),
				eq(new RealtimeMessage(type, data)));
	}

	private Attendance respond(Member member, AttendanceStatus status) {
		return Attendance.respond(round, member, status, RESPONDED_AT);
	}

	private static BoardGameRankRequest rank(Member member, int rank) {
		return new BoardGameRankRequest(member.getId(), rank);
	}

	private static BoardGameRecordRequest request(String gameName, BoardGameRankRequest... ranks) {
		return new BoardGameRecordRequest(gameName, List.of(ranks));
	}
}
