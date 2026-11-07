package com.poco7250.moyeo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.place.dto.PlaceDecisionRequiredResponse;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 마감 처리와 수동 확정의 실제 커밋 순서를 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 * 첫째 후보 1표, 둘째 후보 0표로 준비해 자동 확정(첫째)과 수동 확정(둘째)의 결과를 구분한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlaceVoteCloseIntegrationTest {

	private static final LocalDateTime PAST_DEADLINE = LocalDateTime.of(2020, 1, 1, 18, 0);
	private static final LocalDateTime FUTURE_DEADLINE = LocalDateTime.of(2099, 12, 19, 18, 0);

	@Autowired
	PlaceVoteCloser placeVoteCloser;

	@Autowired
	PlaceVoteCloseProcessor placeVoteCloseProcessor;

	@Autowired
	RoundPlaceService roundPlaceService;

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
	private Group group;
	private Meeting meeting;
	private Round round;
	private PlaceCandidate first;
	private PlaceCandidate second;

	@BeforeEach
	void setUp() {
		host = memberRepository.save(Member.ofKakao(5001L, "호스트", null, null));
		group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.save(GroupMember.join(group, host, "호스트", GroupRole.OWNER));
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "밥"));
		roundPlaceService.openVoting(round.getId(), host.getId(), PAST_DEADLINE);
		first = placeCandidateRepository.save(PlaceCandidate.propose(round, Place.of("고깃집", null, null), host));
		second = placeCandidateRepository.save(PlaceCandidate.propose(round, Place.of("횟집", null, null), host));
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		// 차수와 후보가 서로를 참조하므로 확정을 먼저 풀어야 지울 수 있다
		transactionTemplate.executeWithoutResult(status -> {
			Round current = roundRepository.findById(round.getId()).orElseThrow();
			if (current.getPlaceStatus() == PlaceStatus.CONFIRMED) {
				current.openVoting(FUTURE_DEADLINE);
			}
		});
		transactionTemplate.executeWithoutResult(status -> placeVoteRepository.deleteAllByRoundId(round.getId()));
		placeCandidateRepository.deleteAll(placeCandidateRepository.findAllByRoundIdOrderById(round.getId()));
		roundRepository.deleteById(round.getId());
		meetingRepository.deleteById(meeting.getId());
		groupMemberRepository.deleteAll(groupMemberRepository.findAllByGroupIdOrderByIdAsc(group.getId()));
		groupRepository.deleteById(group.getId());
		memberRepository.deleteById(host.getId());
	}

	private void voteFirst() {
		placeVoteRepository.save(PlaceVote.cast(first, host));
	}

	/** 커밋된 차수의 확정 후보 id를 읽는다. */
	private Long confirmedCandidateId() {
		return transactionTemplate.execute(status -> {
			PlaceCandidate confirmed = roundRepository.findById(round.getId()).orElseThrow().getConfirmedCandidate();
			return confirmed == null ? null : confirmed.getId();
		});
	}

	private void assertSingleBroadcast() {
		verify(messagingTemplate, times(1)).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void 단독_최다면_스케줄러_실행으로_확정되고_변경이_브로드캐스트된다() {
		voteFirst();

		placeVoteCloser.closeExpired();

		assertThat(confirmedCandidateId()).isEqualTo(first.getId());
		assertSingleBroadcast();
	}

	@Test
	void 동점이면_대기_상태로_남고_두_번_돌려도_선택_요청은_한_번만_나간다() {
		placeVoteCloser.closeExpired();
		placeVoteCloser.closeExpired();

		Round current = roundRepository.findById(round.getId()).orElseThrow();
		assertThat(current.getPlaceStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(current.isAwaitingHostDecision()).isTrue();
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(PlaceDecisionRequiredEvent.TYPE,
						new PlaceDecisionRequiredResponse(round.getId(), List.of(first.getId(), second.getId()))));
		assertSingleBroadcast();
	}

	@Test
	void 선택_대기_중_재투표를_열면_표가_지워지고_새_마감_뒤_다시_처리된다() {
		// 1:1 동점으로 대기 상태를 만든다
		voteFirst();
		placeVoteRepository.save(PlaceVote.cast(second, host));
		placeVoteCloser.closeExpired();

		roundPlaceService.openVoting(round.getId(), host.getId(), PAST_DEADLINE.plusDays(1));

		assertThat(placeVoteRepository.findAllByRoundId(round.getId())).isEmpty();
		assertThat(placeCandidateRepository.findAllByRoundIdOrderById(round.getId())).hasSize(2);
		assertThat(roundRepository.findVoteClosableIds(LocalDateTime.now())).contains(round.getId());
		voteFirst();
		placeVoteCloser.closeExpired();
		assertThat(confirmedCandidateId()).isEqualTo(first.getId());
	}

	@Test
	void 수동_확정이_먼저_커밋되면_늦은_마감_처리는_실패하고_수동_확정이_남는다() {
		voteFirst();

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			placeVoteCloseProcessor.close(round.getId());
			// 마감 처리가 아직 커밋되지 않은 사이에 수동 확정이 먼저 커밋된다
			CompletableFuture.runAsync(() ->
							roundPlaceService.confirmManually(round.getId(), host.getId(), second.getId()))
					.orTimeout(30, TimeUnit.SECONDS).join();
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(confirmedCandidateId()).isEqualTo(second.getId());
		assertSingleBroadcast();
	}

	@Test
	void 마감_처리가_먼저_커밋되면_늦은_수동_확정은_충돌_예외가_되고_자동_확정이_남는다() {
		voteFirst();

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			roundPlaceService.confirmManually(round.getId(), host.getId(), second.getId());
			// 수동 확정이 아직 커밋되지 않은 사이에 마감 처리가 먼저 커밋된다
			CompletableFuture.runAsync(() -> placeVoteCloseProcessor.close(round.getId()))
					.orTimeout(30, TimeUnit.SECONDS).join();
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(confirmedCandidateId()).isEqualTo(first.getId());
		assertSingleBroadcast();
	}

	@Test
	void 마감_처리_뒤에_들어온_수동_확정은_409_상태_전이_에러다() {
		voteFirst();
		placeVoteCloser.closeExpired();

		assertThatThrownBy(() -> roundPlaceService.confirmManually(round.getId(), host.getId(), second.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		assertThat(confirmedCandidateId()).isEqualTo(first.getId());
	}

	@Test
	void 마감_처리와_수동_확정을_동시에_실행해도_하나만_반영되고_늦은_쪽은_충돌_응답이다() {
		voteFirst();
		CountDownLatch start = new CountDownLatch(1);
		CompletableFuture<Void> closing = CompletableFuture.runAsync(() -> {
			await(start);
			placeVoteCloser.closeExpired();
		});
		CompletableFuture<Throwable> confirming = CompletableFuture.supplyAsync(() -> {
			await(start);
			return confirmManuallyCapturingFailure();
		});

		start.countDown();
		closing.orTimeout(30, TimeUnit.SECONDS).join();
		Throwable failure = confirming.orTimeout(30, TimeUnit.SECONDS).join();

		if (failure == null) {
			assertThat(confirmedCandidateId()).isEqualTo(second.getId());
		} else {
			assertThat(confirmedCandidateId()).isEqualTo(first.getId());
			assertThat(isConflict(failure)).isTrue();
		}
		assertSingleBroadcast();
	}

	private Throwable confirmManuallyCapturingFailure() {
		try {
			roundPlaceService.confirmManually(round.getId(), host.getId(), second.getId());
			return null;
		} catch (RuntimeException e) {
			return e;
		}
	}

	private boolean isConflict(Throwable failure) {
		return failure instanceof ObjectOptimisticLockingFailureException
				|| (failure instanceof BusinessException business
						&& business.getErrorCode() == ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
