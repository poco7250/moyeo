package com.poco7250.moyeo.place.service;

import static com.poco7250.moyeo.place.service.PlaceServiceFixture.HOST_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEETING_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEMBER_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.ROUND_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.service.GameDrawer;
import com.poco7250.moyeo.game.service.GameSeedGenerator;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceRoulette;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.place.dto.PlaceRouletteResultResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceRouletteRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
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
class PlaceRouletteServiceTest {

	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);
	private static final LocalDateTime NOW = DEADLINE.plusMinutes(1);
	private static final long SEED = 42L;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	RoundRepository roundRepository;

	@Mock
	PlaceCandidateRepository placeCandidateRepository;

	@Mock
	PlaceVoteRepository placeVoteRepository;

	@Mock
	PlaceRouletteRepository placeRouletteRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	GameSeedGenerator seedGenerator;

	@Mock
	ApplicationEventPublisher eventPublisher;

	private PlaceRouletteService service;
	private Round round;
	private PlaceCandidate first;
	private PlaceCandidate second;
	private PlaceCandidate third;

	@BeforeEach
	void setUp() {
		service = new PlaceRouletteService(meetingAccessChecker, roundRepository,
				new RoundVoteDecider(placeCandidateRepository, placeVoteRepository), placeRouletteRepository,
				memberRepository, seedGenerator, new RoundPlaceSnapshot(placeCandidateRepository, eventPublisher),
				eventPublisher, TestClocks.fixed(NOW));
		round = PlaceServiceFixture.round();
		first = PlaceServiceFixture.candidate(round, 500L, "고깃집");
		second = PlaceServiceFixture.candidate(round, 501L, "횟집");
		third = PlaceServiceFixture.candidate(round, 502L, "파스타");
	}

	private void givenAwaitingDecision(List<PlaceCandidate> candidates, List<PlaceVote> votes) {
		round.openVoting(DEADLINE);
		round.markVoteClosed(NOW);
		given(meetingAccessChecker.requireRoundHost(ROUND_ID, HOST_ID)).willReturn(round);
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID)).willReturn(candidates);
		given(placeVoteRepository.findAllByRoundId(ROUND_ID)).willReturn(votes);
	}

	private void givenSpinSucceeds() {
		given(seedGenerator.next()).willReturn(SEED);
		given(memberRepository.getReferenceById(HOST_ID)).willReturn(PlaceServiceFixture.member(HOST_ID));
		given(placeRouletteRepository.save(any(PlaceRoulette.class))).willAnswer(invocation -> {
			PlaceRoulette saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 900L);
			return saved;
		});
	}

	private PlaceVote voteFor(PlaceCandidate candidate, Long memberId) {
		return PlaceVote.cast(candidate, PlaceServiceFixture.member(memberId));
	}

	@Test
	@DisplayName("동점 후보끼리 돌려 당첨 후보로 확정하고 기록 저장·PLACE_CHANGED·PLACE_ROULETTE_SPUN을 발행한다")
	void spinAmongTied() {
		givenAwaitingDecision(List.of(first, second, third),
				List.of(voteFor(first, MEMBER_ID), voteFor(second, 9L), voteFor(third, 9L), voteFor(third, 10L),
						voteFor(first, 10L)));
		givenSpinSucceeds();

		PlaceRouletteResultResponse result = service.spin(ROUND_ID, HOST_ID);

		List<Long> expected = GameDrawer.order(List.of(500L, 502L), SEED);
		assertThat(result.roulette().candidateIds()).containsExactlyElementsOf(expected);
		assertThat(result.roulette().winnerCandidateId()).isEqualTo(expected.get(0));
		assertThat(result.roulette().spunAt()).isEqualTo(NOW);
		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(round.getConfirmedCandidate().getId()).isEqualTo(expected.get(0));
		assertThat(result.place().confirmedCandidateId()).isEqualTo(expected.get(0));
		verify(eventPublisher).publishEvent(any(PlaceChangedEvent.class));
		verify(eventPublisher).publishEvent(new PlaceRouletteSpunEvent(MEETING_ID, result.roulette()));
		verify(roundRepository).flush();
	}

	@Test
	@DisplayName("전원 0표면 후보 전체가 룰렛 대상이다")
	void zeroVotesUsesAllCandidates() {
		givenAwaitingDecision(List.of(first, second, third), List.of());
		givenSpinSucceeds();

		service.spin(ROUND_ID, HOST_ID);

		ArgumentCaptor<PlaceRoulette> captor = ArgumentCaptor.forClass(PlaceRoulette.class);
		verify(placeRouletteRepository).save(captor.capture());
		assertThat(captor.getValue().getCandidateIds()).containsExactlyInAnyOrder(500L, 501L, 502L);
	}

	@Test
	@DisplayName("후보가 1개(0표)뿐이면 409 PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES이고 확정하지 않는다")
	void singleCandidateRejected() {
		givenAwaitingDecision(List.of(first), List.of());

		assertThatThrownBy(() -> service.spin(ROUND_ID, HOST_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PLACE_ROULETTE_NOT_ENOUGH_CANDIDATES);
		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.VOTING);
		verify(placeRouletteRepository, never()).save(any());
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("마감 전 투표 중·확정·미정 차수는 409 ROUND_PLACE_INVALID_TRANSITION")
	void notAwaitingRejected() {
		given(meetingAccessChecker.requireRoundHost(ROUND_ID, HOST_ID)).willReturn(round);

		assertInvalidTransition();
		round.openVoting(DEADLINE);
		assertInvalidTransition();
		round.confirm(first);
		assertInvalidTransition();
		verifyNoInteractions(placeCandidateRepository, placeVoteRepository, placeRouletteRepository, roundRepository,
				eventPublisher);
	}

	private void assertInvalidTransition() {
		assertThatThrownBy(() -> service.spin(ROUND_ID, HOST_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
	}

	@Test
	@DisplayName("호스트가 아니면 403 NOT_MEETING_HOST")
	void notHostRejected() {
		given(meetingAccessChecker.requireRoundHost(ROUND_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		assertThatThrownBy(() -> service.spin(ROUND_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_MEETING_HOST);
		verifyNoInteractions(placeRouletteRepository, eventPublisher);
	}

	@Test
	@DisplayName("기록 조회는 멤버 검사 뒤 최근 순 목록을 준다")
	void history() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID)).willReturn(round);
		PlaceRoulette roulette = PlaceRoulette.spin(round, List.of(first, second), SEED,
				PlaceServiceFixture.member(HOST_ID), NOW);
		ReflectionTestUtils.setField(roulette, "id", 900L);
		given(placeRouletteRepository.findAllByRoundIdOrderByIdDesc(ROUND_ID)).willReturn(List.of(roulette));

		assertThat(service.getHistory(ROUND_ID, MEMBER_ID))
				.singleElement()
				.satisfies(response -> {
					assertThat(response.id()).isEqualTo(900L);
					assertThat(response.roundId()).isEqualTo(ROUND_ID);
					assertThat(response.spunByMemberId()).isEqualTo(HOST_ID);
				});
	}
}
