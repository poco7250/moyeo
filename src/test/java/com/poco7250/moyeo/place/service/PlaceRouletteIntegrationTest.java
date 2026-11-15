package com.poco7250.moyeo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.game.service.GameDrawer;
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
import com.poco7250.moyeo.place.dto.PlaceRouletteResponse;
import com.poco7250.moyeo.place.dto.PlaceRouletteResultResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceRouletteRepository;
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
 * 장소 동점 룰렛(P-6). 마감 처리로 실제 선택 대기를 만들고, 수동 확정과의 커밋 순서를 봐야 해서
 * (룰렛은 차수 갱신을 먼저 flush하므로 룰렛이 쓰는 중엔 수동 확정이 행 잠금을 기다린다)
 * 테스트 트랜잭션 없이 직접 데이터를 지운다. 두 후보 모두 0표라 전원 0표 동점(후보 전체가 대상)이다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlaceRouletteIntegrationTest {

	private static final LocalDateTime PAST_DEADLINE = LocalDateTime.of(2020, 1, 1, 18, 0);
	private static final LocalDateTime FUTURE_DEADLINE = LocalDateTime.of(2099, 12, 19, 18, 0);

	@Autowired
	PlaceRouletteService placeRouletteService;

	@Autowired
	PlaceVoteCloser placeVoteCloser;

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
	PlaceRouletteRepository placeRouletteRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Member host;
	private Member outsider;
	private Group group;
	private Meeting meeting;
	private Round round;
	private PlaceCandidate first;
	private PlaceCandidate second;

	@BeforeEach
	void setUp() {
		host = memberRepository.save(Member.ofKakao(5101L, "호스트", null, null));
		outsider = memberRepository.save(Member.ofKakao(5102L, "외부인", null, null));
		group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.save(GroupMember.join(group, host, "호스트", GroupRole.OWNER));
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, host.getId()));
		round = roundRepository.save(Round.create(meeting, 1, "밥"));
		roundPlaceService.openVoting(round.getId(), host.getId(), PAST_DEADLINE);
		first = placeCandidateRepository.save(PlaceCandidate.propose(round, Place.of("고깃집", null, null), host));
		second = placeCandidateRepository.save(PlaceCandidate.propose(round, Place.of("횟집", null, null), host));
		placeVoteCloser.closeExpired();
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		// 룰렛 기록이 후보를, 차수와 후보가 서로를 참조하므로 기록 → 확정 해제 순으로 지운다
		placeRouletteRepository.deleteAll(placeRouletteRepository.findAllByRoundIdOrderByIdDesc(round.getId()));
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
		memberRepository.deleteAllById(List.of(host.getId(), outsider.getId()));
	}

	/** 커밋된 차수의 확정 후보 id를 읽는다. */
	private Long confirmedCandidateId() {
		return transactionTemplate.execute(status -> {
			PlaceCandidate confirmed = roundRepository.findById(round.getId()).orElseThrow().getConfirmedCandidate();
			return confirmed == null ? null : confirmed.getId();
		});
	}

	@Test
	void 선택_대기_중_룰렛을_돌리면_당첨_후보로_확정되고_기록이_남고_두_메시지가_나간다() {
		PlaceRouletteResultResponse result = placeRouletteService.spin(round.getId(), host.getId());

		PlaceRouletteResponse roulette = result.roulette();
		assertThat(roulette.candidateIds())
				.containsExactlyElementsOf(GameDrawer.order(List.of(first.getId(), second.getId()), roulette.seed()));
		assertThat(roulette.winnerCandidateId()).isEqualTo(roulette.candidateIds().get(0));
		assertThat(confirmedCandidateId()).isEqualTo(roulette.winnerCandidateId());
		assertThat(placeRouletteService.getHistory(round.getId(), host.getId())).containsExactly(roulette);
		String topic = "/topic/meetings/" + meeting.getId();
		verify(messagingTemplate).convertAndSend(eq(topic),
				eq(new RealtimeMessage(PlaceChangedEvent.TYPE, result.place())));
		verify(messagingTemplate).convertAndSend(eq(topic),
				eq(new RealtimeMessage(PlaceRouletteSpunEvent.TYPE, roulette)));
		verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void 확정_후에는_다시_돌릴_수_없고_재투표_뒤_다시_동점이면_기록이_최근_순으로_쌓인다() {
		PlaceRouletteResponse firstSpin = placeRouletteService.spin(round.getId(), host.getId()).roulette();
		assertThatThrownBy(() -> placeRouletteService.spin(round.getId(), host.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);

		roundPlaceService.openVoting(round.getId(), host.getId(), PAST_DEADLINE.plusDays(1));
		placeVoteCloser.closeExpired();
		PlaceRouletteResponse secondSpin = placeRouletteService.spin(round.getId(), host.getId()).roulette();

		assertThat(placeRouletteService.getHistory(round.getId(), host.getId()))
				.containsExactly(secondSpin, firstSpin);
		assertThat(confirmedCandidateId()).isEqualTo(secondSpin.winnerCandidateId());
	}

	@Test
	void 룰렛이_먼저_커밋되면_늦은_수동_확정은_충돌_예외가_되고_룰렛_결과가_남는다() {
		CompletableFuture<PlaceRouletteResponse> spinning = new CompletableFuture<>();

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			roundPlaceService.confirmManually(round.getId(), host.getId(), second.getId());
			// 수동 확정이 아직 커밋되지 않은 사이에 룰렛이 먼저 커밋된다
			spinning.complete(CompletableFuture
					.supplyAsync(() -> placeRouletteService.spin(round.getId(), host.getId()).roulette())
					.orTimeout(30, TimeUnit.SECONDS).join());
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);

		PlaceRouletteResponse roulette = spinning.join();
		assertThat(confirmedCandidateId()).isEqualTo(roulette.winnerCandidateId());
		assertThat(placeRouletteRepository.findAllByRoundIdOrderByIdDesc(round.getId())).hasSize(1);
	}

	@Test
	void 수동_확정_뒤에_들어온_룰렛은_409이고_기록이_남지_않는다() {
		roundPlaceService.confirmManually(round.getId(), host.getId(), second.getId());

		assertThatThrownBy(() -> placeRouletteService.spin(round.getId(), host.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		assertThat(confirmedCandidateId()).isEqualTo(second.getId());
		assertThat(placeRouletteRepository.findAllByRoundIdOrderByIdDesc(round.getId())).isEmpty();
	}

	@Test
	void 룰렛과_수동_확정을_동시에_실행해도_하나만_반영되고_기록은_룰렛이_이겼을_때만_남는다() {
		CountDownLatch start = new CountDownLatch(1);
		CompletableFuture<Throwable> spinning = CompletableFuture.supplyAsync(() -> {
			await(start);
			return capturingFailure(() -> placeRouletteService.spin(round.getId(), host.getId()));
		});
		CompletableFuture<Throwable> confirming = CompletableFuture.supplyAsync(() -> {
			await(start);
			return capturingFailure(() -> roundPlaceService.confirmManually(round.getId(), host.getId(),
					second.getId()));
		});

		start.countDown();
		Throwable spinFailure = spinning.orTimeout(30, TimeUnit.SECONDS).join();
		Throwable confirmFailure = confirming.orTimeout(30, TimeUnit.SECONDS).join();

		// 정확히 한쪽만 실패하고, 실패한 쪽은 충돌 응답(409)이다
		assertThat(spinFailure == null).isNotEqualTo(confirmFailure == null);
		List<PlaceRouletteResponse> history = placeRouletteService.getHistory(round.getId(), host.getId());
		if (spinFailure == null) {
			assertThat(isConflict(confirmFailure)).isTrue();
			assertThat(history).singleElement()
					.satisfies(roulette -> assertThat(confirmedCandidateId()).isEqualTo(roulette.winnerCandidateId()));
		} else {
			assertThat(isConflict(spinFailure)).isTrue();
			assertThat(history).isEmpty();
			assertThat(confirmedCandidateId()).isEqualTo(second.getId());
		}
	}

	private Throwable capturingFailure(Runnable action) {
		try {
			action.run();
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

	@Test
	void 그룹_밖_회원은_기록을_조회할_수_없다() {
		assertThatThrownBy(() -> placeRouletteService.getHistory(round.getId(), outsider.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}
}
