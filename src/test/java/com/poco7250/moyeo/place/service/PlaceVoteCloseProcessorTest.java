package com.poco7250.moyeo.place.service;

import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEETING_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEMBER_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.ROUND_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.place.dto.PlaceDecisionRequiredResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PlaceVoteCloseProcessorTest {

	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);
	private static final LocalDateTime NOW = DEADLINE.plusSeconds(1);

	@Mock
	RoundRepository roundRepository;

	@Mock
	PlaceCandidateRepository placeCandidateRepository;

	@Mock
	PlaceVoteRepository placeVoteRepository;

	@Mock
	ApplicationEventPublisher eventPublisher;

	private PlaceVoteCloseProcessor processor;
	private Round round;
	private PlaceCandidate first;
	private PlaceCandidate second;

	@BeforeEach
	void setUp() {
		processor = new PlaceVoteCloseProcessor(roundRepository, placeCandidateRepository, placeVoteRepository,
				new RoundPlaceSnapshot(placeCandidateRepository, eventPublisher), eventPublisher,
				TestClocks.fixed(NOW));
		round = PlaceServiceFixture.round();
		first = PlaceServiceFixture.candidate(round, 500L, "고깃집");
		second = PlaceServiceFixture.candidate(round, 501L, "횟집");
		given(roundRepository.findById(ROUND_ID)).willReturn(Optional.of(round));
	}

	private void givenVotingWith(List<PlaceCandidate> candidates, List<PlaceVote> votes) {
		round.openVoting(DEADLINE);
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID)).willReturn(candidates);
		given(placeVoteRepository.findAllByRoundId(ROUND_ID)).willReturn(votes);
	}

	private PlaceVote voteFor(PlaceCandidate candidate, Long memberId) {
		return PlaceVote.cast(candidate, PlaceServiceFixture.member(memberId));
	}

	private void assertDecisionRequested(List<Long> tiedCandidateIds) {
		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(round.isAwaitingHostDecision()).isTrue();
		assertThat(round.getConfirmedCandidate()).isNull();
		verify(eventPublisher).publishEvent(new PlaceDecisionRequiredEvent(MEETING_ID,
				new PlaceDecisionRequiredResponse(ROUND_ID, tiedCandidateIds)));
	}

	@Test
	@DisplayName("단독 최다면 그 후보로 확정하고 PLACE_CHANGED를 발행한다")
	void singleWinner() {
		givenVotingWith(List.of(first, second),
				List.of(voteFor(second, MEMBER_ID), voteFor(second, 9L), voteFor(first, MEMBER_ID)));

		processor.close(ROUND_ID);

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(round.getConfirmedCandidate()).isSameAs(second);
		assertThat(round.isAwaitingHostDecision()).isFalse();
		verify(eventPublisher).publishEvent(any(PlaceChangedEvent.class));
	}

	@Test
	@DisplayName("동점이면 VOTING을 유지하고 동점 후보로 호스트 선택을 요청한다")
	void tie() {
		PlaceCandidate third = PlaceServiceFixture.candidate(round, 502L, "카페");
		givenVotingWith(List.of(first, second, third), List.of(voteFor(first, MEMBER_ID), voteFor(second, MEMBER_ID)));

		processor.close(ROUND_ID);

		assertDecisionRequested(List.of(500L, 501L));
	}

	@Test
	@DisplayName("후보가 1개여도 0표면 호스트 선택을 요청한다")
	void zeroVotesSingleCandidate() {
		givenVotingWith(List.of(first), List.of());

		processor.close(ROUND_ID);

		assertDecisionRequested(List.of(500L));
	}

	@Test
	@DisplayName("후보가 없으면 빈 목록으로 호스트 선택을 요청한다")
	void noCandidates() {
		givenVotingWith(List.of(), List.of());

		processor.close(ROUND_ID);

		assertDecisionRequested(List.of());
	}

	@Test
	@DisplayName("이미 확정된 차수는 아무것도 하지 않는다")
	void alreadyConfirmed() {
		round.openVoting(DEADLINE);
		round.confirm(first);

		processor.close(ROUND_ID);

		assertThat(round.getConfirmedCandidate()).isSameAs(first);
		verifyNoInteractions(placeCandidateRepository, placeVoteRepository, eventPublisher);
	}

	@Test
	@DisplayName("이미 호스트 선택 대기면 선택 요청을 다시 보내지 않는다")
	void alreadyAwaiting() {
		round.openVoting(DEADLINE);
		round.markVoteClosed(NOW);

		processor.close(ROUND_ID);

		verifyNoInteractions(placeCandidateRepository, placeVoteRepository, eventPublisher);
	}

	@Test
	@DisplayName("재투표로 마감이 미뤄졌으면 아무것도 하지 않는다")
	void deadlineNotPassed() {
		round.openVoting(NOW.plusDays(1));

		processor.close(ROUND_ID);

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.VOTING);
		verifyNoInteractions(placeCandidateRepository, placeVoteRepository, eventPublisher);
	}
}
