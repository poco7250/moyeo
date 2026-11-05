package com.poco7250.moyeo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.BoardMember;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.dto.CandidateVotes;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RoundVotesResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 투표 쓰기와 집계가 별도 트랜잭션으로 커밋되는지 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlaceVoteServiceIntegrationTest {

	private static final LocalDateTime DEADLINE = LocalDateTime.of(2099, 12, 19, 18, 0);

	@Autowired
	PlaceVoteService placeVoteService;

	@Autowired
	RoundPlaceService roundPlaceService;

	@Autowired
	PlaceCandidateService placeCandidateService;

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
	PlaceCandidateRepository placeCandidateRepository;

	@Autowired
	PlaceVoteRepository placeVoteRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Member host;
	private Member member;
	private Group group;
	private Meeting meeting;
	private Round round;
	private Long firstId;
	private Long secondId;

	@BeforeEach
	void setUp() {
		host = memberRepository.save(Member.ofKakao(4001L, "호스트", null, null));
		member = memberRepository.save(Member.ofKakao(4002L, "멤버", null, null));
		group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.save(GroupMember.join(group, host, "호스트", GroupRole.OWNER));
		groupMemberRepository.save(GroupMember.join(group, member, "멤버", GroupRole.MEMBER));
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "밥"));
		roundPlaceService.openVoting(round.getId(), host.getId(), DEADLINE);
		firstId = placeCandidateService.add(round.getId(), member.getId(), new PlaceInput("고깃집", null, null)).id();
		secondId = placeCandidateService.add(round.getId(), member.getId(), new PlaceInput("횟집", null, null)).id();
		// 준비 과정의 PLACE_CHANGED는 검증 대상이 아니다
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> placeVoteRepository.deleteAllByRoundId(round.getId()));
		placeCandidateRepository.deleteAll(placeCandidateRepository.findAllByRoundIdOrderById(round.getId()));
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meeting.getId());
		groupMemberRepository.deleteAll(groupMemberRepository.findAllByGroupIdOrderByIdAsc(group.getId()));
		groupRepository.deleteById(group.getId());
		memberRepository.deleteAllById(List.of(host.getId(), member.getId()));
	}

	@Test
	void 후보_2곳에_투표하면_2표가_저장되고_커밋된_현황이_모임_토픽으로_나간다() {
		placeVoteService.vote(round.getId(), firstId, member.getId());
		RoundVotesResponse response = placeVoteService.vote(round.getId(), secondId, member.getId());

		BoardMember voter = new BoardMember(member.getId(), "멤버", null);
		assertThat(response.candidates()).containsExactly(
				new CandidateVotes(firstId, 1, List.of(voter)),
				new CandidateVotes(secondId, 1, List.of(voter)));
		assertThat(placeVoteRepository.findAllByRoundId(round.getId())).hasSize(2);
		verify(messagingTemplate).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(VoteChangedEvent.TYPE, response));
	}

	@Test
	void 같은_후보에_다시_투표하면_1표가_유지되고_다시_발행하지_않는다() {
		placeVoteService.vote(round.getId(), firstId, member.getId());
		clearInvocations(messagingTemplate);

		RoundVotesResponse response = placeVoteService.vote(round.getId(), firstId, member.getId());

		assertThat(response.candidates().get(0).voteCount()).isEqualTo(1);
		assertThat(placeVoteRepository.findAllByRoundId(round.getId())).hasSize(1);
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void 표를_취소하면_득표가_줄고_현황이_나간다() {
		placeVoteService.vote(round.getId(), firstId, member.getId());
		placeVoteService.vote(round.getId(), firstId, host.getId());

		RoundVotesResponse response = placeVoteService.cancel(round.getId(), firstId, member.getId());

		assertThat(response.candidates().get(0)).isEqualTo(new CandidateVotes(firstId, 1,
				List.of(new BoardMember(host.getId(), "호스트", null))));
		assertThat(placeVoteService.getVotes(round.getId(), member.getId())).isEqualTo(response);
		verify(messagingTemplate).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(VoteChangedEvent.TYPE, response));
	}

	@Test
	void 같은_표가_동시에_들어와도_둘_다_성공하고_1건만_저장된다() {
		CountDownLatch start = new CountDownLatch(1);
		List<CompletableFuture<RoundVotesResponse>> votes = List.of(concurrentVote(start), concurrentVote(start));

		start.countDown();
		List<RoundVotesResponse> responses = votes.stream()
				.map(vote -> vote.orTimeout(30, TimeUnit.SECONDS).join())
				.toList();

		assertThat(responses).allSatisfy(response ->
				assertThat(response.candidates().get(0).voteCount()).isEqualTo(1));
		assertThat(placeVoteRepository.findAllByRoundId(round.getId())).hasSize(1);
	}

	private CompletableFuture<RoundVotesResponse> concurrentVote(CountDownLatch start) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				start.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			return placeVoteService.vote(round.getId(), firstId, member.getId());
		});
	}
}
