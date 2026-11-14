package com.poco7250.moyeo.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.MeetingDateStatus;
import com.poco7250.moyeo.meeting.domain.RoundTemplate;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.dto.DateCandidateVotes;
import com.poco7250.moyeo.meeting.dto.MeetingCreateRequest;
import com.poco7250.moyeo.meeting.dto.MeetingDateConfirmedResponse;
import com.poco7250.moyeo.meeting.dto.MeetingDateVotesResponse;
import com.poco7250.moyeo.meeting.dto.MeetingDetailResponse;
import com.poco7250.moyeo.meeting.dto.MeetingSummaryResponse;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.ConcurrentRunner;
import com.poco7250.moyeo.support.ConcurrentRunner.Outcome;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 날짜 후보 투표(PRD M-5)를 실제 DB로 확인한다. 실제 커밋·동시 실행을 봐야 해서 테스트 트랜잭션을 쓰지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MeetingDateIntegrationTest {

	private static final LocalDateTime FIRST = LocalDateTime.of(2099, 12, 20, 18, 0);
	private static final LocalDateTime SECOND = LocalDateTime.of(2099, 12, 21, 19, 0);
	private static final LocalDateTime THIRD = LocalDateTime.of(2099, 12, 22, 12, 0);

	@Autowired
	MeetingService meetingService;

	@Autowired
	MeetingDateService meetingDateService;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Member host;
	private Member minsu;
	private Member yeonghee;
	private Group group;
	private Long meetingId;
	private Long first;
	private Long second;

	@BeforeEach
	void setUp() {
		host = saveMember("철수");
		minsu = saveMember("민수");
		yeonghee = saveMember("영희");
		group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.saveAll(List.of(join(host, GroupRole.OWNER), join(minsu, GroupRole.MEMBER),
				join(yeonghee, GroupRole.MEMBER)));
		// 후보를 늦은 시각부터 보내도 이른 시각 순으로 저장·조회된다
		meetingId = createVotingMeeting(List.of(SECOND, FIRST)).id();
		List<DateCandidateVotes> candidates = meetingDateService.getVotes(meetingId, host.getId()).candidates();
		first = candidates.get(0).candidateId();
		second = candidates.get(1).candidateId();
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("날짜 후보로 만든 모임은 시작 시각 없이 VOTING이고, 공유 문구에 날짜 투표 중이 들어가며, 목록에서 다가오는 모임 맨 앞이다")
	void votingMeetingCreated() {
		MeetingDetailResponse dated = meetingService.create(group.getId(), host.getId(), new MeetingCreateRequest(
				"송년회", THIRD, null, null, RoundTemplate.MEAL_ONLY, null, null));

		MeetingDetailResponse detail = meetingService.getDetail(meetingId, minsu.getId());
		MeetingDateVotesResponse votes = meetingDateService.getVotes(meetingId, minsu.getId());

		assertThat(detail.startAt()).isNull();
		assertThat(detail.dateStatus()).isEqualTo(MeetingDateStatus.VOTING);
		assertThat(detail.shareText()).contains("날짜 투표 중");
		assertThat(votes.candidates()).extracting(DateCandidateVotes::startAt).containsExactly(FIRST, SECOND);
		assertThat(meetingService.getGroupMeetings(group.getId(), minsu.getId()).upcoming())
				.extracting(MeetingSummaryResponse::id).containsExactly(meetingId, dated.id());
	}

	@Test
	@DisplayName("멤버별 가능 후보를 후보마다 가능·불가·미응답으로 집계하고, 같은 응답 재전송은 알림 없이 그대로다")
	void respondAndTally() {
		meetingDateService.respond(meetingId, minsu.getId(), List.of(first));
		MeetingDateVotesResponse votes = meetingDateService.respond(meetingId, yeonghee.getId(), List.of(first, second));

		assertCandidate(votes.candidates().get(0), List.of(minsu, yeonghee), List.of(), List.of(host));
		assertCandidate(votes.candidates().get(1), List.of(yeonghee), List.of(minsu), List.of(host));
		assertThat(sentTypes()).containsExactly(DateVoteChangedEvent.TYPE, DateVoteChangedEvent.TYPE);

		clearInvocations(messagingTemplate);
		meetingDateService.respond(meetingId, minsu.getId(), List.of(first));
		assertThat(sentTypes()).isEmpty();
		assertThat(voteRows(minsu)).isEqualTo(2);
	}

	@Test
	@DisplayName("응답 뒤에 추가된 후보는 이미 응답한 멤버도 미응답이고, 다시 응답하면 그 후보에도 행이 생긴다")
	void candidateAddedAfterResponse() {
		meetingDateService.respond(meetingId, minsu.getId(), List.of(first));

		MeetingDateVotesResponse added = meetingDateService.addCandidate(meetingId, host.getId(), THIRD);
		assertCandidate(added.candidates().get(2), List.of(), List.of(), List.of(host, minsu, yeonghee));

		MeetingDateVotesResponse answered = meetingDateService.respond(meetingId, minsu.getId(),
				List.of(added.candidates().get(2).candidateId()));
		assertCandidate(answered.candidates().get(0), List.of(), List.of(minsu), List.of(host, yeonghee));
		assertCandidate(answered.candidates().get(2), List.of(minsu), List.of(), List.of(host, yeonghee));
	}

	@Test
	@DisplayName("후보 추가·확정은 호스트만, 다른 모임 후보 id는 404")
	void hostOnlyAndOwnCandidates() {
		Long foreign = meetingDateService.getVotes(createVotingMeeting(List.of(FIRST, THIRD)).id(), host.getId())
				.candidates().getFirst().candidateId();

		assertError(() -> meetingDateService.addCandidate(meetingId, minsu.getId(), THIRD), ErrorCode.NOT_MEETING_HOST);
		assertError(() -> meetingDateService.confirm(meetingId, minsu.getId(), first), ErrorCode.NOT_MEETING_HOST);
		assertError(() -> meetingDateService.respond(meetingId, minsu.getId(), List.of(foreign)),
				ErrorCode.MEETING_DATE_CANDIDATE_NOT_FOUND);
		assertError(() -> meetingDateService.confirm(meetingId, host.getId(), foreign),
				ErrorCode.MEETING_DATE_CANDIDATE_NOT_FOUND);
		assertThat(meetingService.getDetail(meetingId, host.getId()).dateStatus()).isEqualTo(MeetingDateStatus.VOTING);
	}

	@Test
	@DisplayName("같은 시각 후보는 409, 후보가 10개면 더 못 넣는다")
	void duplicateAndLimit() {
		assertError(() -> meetingDateService.addCandidate(meetingId, host.getId(), FIRST),
				ErrorCode.MEETING_DATE_CANDIDATE_DUPLICATED);
		for (int day = 1; day <= 8; day++) {
			meetingDateService.addCandidate(meetingId, host.getId(), THIRD.plusDays(day));
		}

		assertError(() -> meetingDateService.addCandidate(meetingId, host.getId(), THIRD),
				ErrorCode.MEETING_DATE_CANDIDATE_LIMIT);
		assertThat(meetingDateService.getVotes(meetingId, host.getId()).candidates()).hasSize(10);
	}

	@Test
	@DisplayName("확정하면 시작 시각이 후보 시각이 되고 확정 알림이 1번 가며, 그 뒤 후보 추가·응답·확정은 409다")
	void confirmFixesDate() {
		MeetingDetailResponse detail = meetingDateService.confirm(meetingId, host.getId(), second);

		assertThat(detail.startAt()).isEqualTo(SECOND);
		assertThat(detail.dateStatus()).isEqualTo(MeetingDateStatus.CONFIRMED);
		assertThat(meetingDateService.getVotes(meetingId, minsu.getId()).confirmedCandidateId()).isEqualTo(second);
		assertThat(sentMessages()).containsExactly(new RealtimeMessage(MeetingDateConfirmedEvent.TYPE,
				new MeetingDateConfirmedResponse(meetingId, second, SECOND)));
		assertError(() -> meetingDateService.respond(meetingId, minsu.getId(), List.of(first)),
				ErrorCode.MEETING_DATE_NOT_VOTING);
		assertError(() -> meetingDateService.addCandidate(meetingId, host.getId(), THIRD),
				ErrorCode.MEETING_DATE_NOT_VOTING);
		assertError(() -> meetingDateService.confirm(meetingId, host.getId(), first), ErrorCode.MEETING_DATE_NOT_VOTING);
		assertThat(meetingService.getGroupMeetings(group.getId(), host.getId()).upcoming())
				.singleElement().satisfies(summary -> assertThat(summary.startAt()).isEqualTo(SECOND));
	}

	@RepeatedTest(5)
	@DisplayName("서로 다른 후보로 확정 2건이 동시에 오면 1건만 반영되고 늦은 쪽은 충돌 또는 409, 확정 알림은 1번이다")
	void concurrentConfirm() {
		List<Outcome<MeetingDetailResponse>> outcomes = ConcurrentRunner.runTogether(List.<Callable<MeetingDetailResponse>>of(
				() -> meetingDateService.confirm(meetingId, host.getId(), first),
				() -> meetingDateService.confirm(meetingId, host.getId(), second)));

		assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
		Throwable failure = outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow().failure();
		assertThat(failure instanceof ObjectOptimisticLockingFailureException
				|| (failure instanceof BusinessException business
						&& business.getErrorCode() == ErrorCode.MEETING_DATE_NOT_VOTING))
				.as("늦은 확정의 실패 원인: %s", failure).isTrue();
		LocalDateTime winner = outcomes.get(0).succeeded() ? FIRST : SECOND;
		assertThat(meetingService.getDetail(meetingId, host.getId()).startAt()).isEqualTo(winner);
		assertThat(sentTypes()).containsExactly(MeetingDateConfirmedEvent.TYPE);
	}

	@RepeatedTest(5)
	@DisplayName("같은 회원의 첫 응답 2건이 동시에 와도 둘 다 성공하고 후보마다 행은 1건이다")
	void concurrentFirstResponse() {
		List<Outcome<MeetingDateVotesResponse>> outcomes = ConcurrentRunner.runTogether(
				List.<Callable<MeetingDateVotesResponse>>of(
						() -> meetingDateService.respond(meetingId, minsu.getId(), List.of(first)),
						() -> meetingDateService.respond(meetingId, minsu.getId(), List.of(second))));

		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
		assertThat(voteRows(minsu)).isEqualTo(2);
	}

	private MeetingDetailResponse createVotingMeeting(List<LocalDateTime> candidates) {
		return meetingService.create(group.getId(), host.getId(), new MeetingCreateRequest(
				"연말 모임", null, null, null, RoundTemplate.MEAL_BOWLING, null, candidates));
	}

	private void assertCandidate(DateCandidateVotes votes, List<Member> available, List<Member> unavailable,
			List<Member> unanswered) {
		assertThat(votes.available()).extracting(BoardMember::memberId).containsExactlyElementsOf(ids(available));
		assertThat(votes.unavailable()).extracting(BoardMember::memberId).containsExactlyElementsOf(ids(unavailable));
		assertThat(votes.unanswered()).extracting(BoardMember::memberId).containsExactlyElementsOf(ids(unanswered));
		assertThat(votes.availableCount()).isEqualTo(available.size());
	}

	private static List<Long> ids(List<Member> members) {
		return members.stream().map(Member::getId).toList();
	}

	private static void assertError(ThrowingCallable call, ErrorCode code) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
	}

	/**
	 * 이 모임 토픽으로 보낸 실시간 메시지를 보낸 순서대로 돌려준다.
	 */
	private List<RealtimeMessage> sentMessages() {
		ArgumentCaptor<String> destinations = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate, atLeast(0)).convertAndSend(destinations.capture(), payloads.capture());
		String topic = "/topic/meetings/" + meetingId;
		List<RealtimeMessage> messages = new ArrayList<>();
		for (int i = 0; i < destinations.getAllValues().size(); i++) {
			if (topic.equals(destinations.getAllValues().get(i))
					&& payloads.getAllValues().get(i) instanceof RealtimeMessage message) {
				messages.add(message);
			}
		}
		return messages;
	}

	private List<String> sentTypes() {
		return sentMessages().stream().map(RealtimeMessage::type).toList();
	}

	private int voteRows(Member member) {
		return jdbcTemplate.queryForObject("select count(*) from meeting_date_vote where member_id = ?", Integer.class,
				member.getId());
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private GroupMember join(Member member, GroupRole role) {
		return GroupMember.join(group, member, member.getNickname(), role);
	}
}
