package com.poco7250.moyeo.place.service;

import static com.poco7250.moyeo.place.service.PlaceServiceFixture.HOST_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEMBER_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.ROUND_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoundPlaceServiceTest {

	private static final PlaceInput INPUT = new PlaceInput("고깃집", null, "https://place.map.kakao.com/1");
	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	PlaceCandidateRepository placeCandidateRepository;

	@Mock
	PlaceVoteRepository placeVoteRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	ApplicationEventPublisher eventPublisher;

	private RoundPlaceService service;
	private Round round;

	@BeforeEach
	void setUp() {
		service = new RoundPlaceService(meetingAccessChecker, placeCandidateRepository, placeVoteRepository,
				memberRepository, new RoundPlaceSnapshot(placeCandidateRepository, eventPublisher));
		round = PlaceServiceFixture.round();
	}

	private void givenHost() {
		given(meetingAccessChecker.requireRoundHost(ROUND_ID, HOST_ID)).willReturn(round);
	}

	@Test
	@DisplayName("바로 확정하면 후보 1개를 만들고 CONFIRMED로 바꾼 뒤 이벤트를 발행한다")
	void confirmDirectly() {
		givenHost();
		given(memberRepository.getReferenceById(HOST_ID)).willReturn(PlaceServiceFixture.member(HOST_ID));
		given(placeCandidateRepository.save(any(PlaceCandidate.class))).willAnswer(invocation -> {
			PlaceCandidate saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 500L);
			return saved;
		});
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID))
				.willAnswer(invocation -> List.of(round.getConfirmedCandidate()));

		RoundPlaceResponse response = service.confirmDirectly(ROUND_ID, HOST_ID, INPUT);

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(response.placeStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(response.confirmedCandidateId()).isEqualTo(500L);
		assertThat(response.candidates()).singleElement().satisfies(c -> {
			assertThat(c.label()).isEqualTo("고깃집");
			assertThat(c.mapUrl()).isEqualTo("https://place.map.kakao.com/1");
			assertThat(c.proposedByMemberId()).isEqualTo(HOST_ID);
		});
		verify(eventPublisher).publishEvent(any(PlaceChangedEvent.class));
	}

	@Test
	@DisplayName("호스트가 아니면 바로 확정할 수 없다(403, 저장 없음)")
	void confirmDirectlyByNonHost() {
		given(meetingAccessChecker.requireRoundHost(ROUND_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		assertThatThrownBy(() -> service.confirmDirectly(ROUND_ID, MEMBER_ID, INPUT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_MEETING_HOST);
		verifyNoInteractions(placeCandidateRepository, eventPublisher);
	}

	@Test
	@DisplayName("투표 중인 차수는 바로 확정할 수 없다(409, 후보 저장 없음)")
	void confirmDirectlyWhileVoting() {
		givenHost();
		round.openVoting(DEADLINE);

		assertThatThrownBy(() -> service.confirmDirectly(ROUND_ID, HOST_ID, INPUT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		verify(placeCandidateRepository, never()).save(any());
	}

	@Test
	@DisplayName("수동 확정하면 고른 후보로 CONFIRMED가 되고 PLACE_CHANGED를 발행한다")
	void confirmManually() {
		givenHost();
		round.openVoting(DEADLINE);
		PlaceCandidate candidate = PlaceServiceFixture.candidate(round, 500L, "고깃집");
		given(placeCandidateRepository.findByIdAndRoundId(500L, ROUND_ID)).willReturn(Optional.of(candidate));
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID)).willReturn(List.of(candidate));

		RoundPlaceResponse response = service.confirmManually(ROUND_ID, HOST_ID, 500L);

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(response.confirmedCandidateId()).isEqualTo(500L);
		verify(eventPublisher).publishEvent(any(PlaceChangedEvent.class));
	}

	@Test
	@DisplayName("호스트 선택 대기 중에도 수동 확정할 수 있고 대기 상태가 풀린다")
	void confirmManuallyWhileAwaiting() {
		givenHost();
		round.openVoting(DEADLINE);
		round.markVoteClosed(DEADLINE.plusSeconds(1));
		PlaceCandidate candidate = PlaceServiceFixture.candidate(round, 500L, "고깃집");
		given(placeCandidateRepository.findByIdAndRoundId(500L, ROUND_ID)).willReturn(Optional.of(candidate));
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID)).willReturn(List.of(candidate));

		RoundPlaceResponse response = service.confirmManually(ROUND_ID, HOST_ID, 500L);

		assertThat(response.placeStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(response.hostDecisionRequired()).isFalse();
	}

	@Test
	@DisplayName("호스트가 아니면 수동 확정할 수 없다(403)")
	void confirmManuallyByNonHost() {
		given(meetingAccessChecker.requireRoundHost(ROUND_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		assertThatThrownBy(() -> service.confirmManually(ROUND_ID, MEMBER_ID, 500L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_MEETING_HOST);
		verifyNoInteractions(placeCandidateRepository, eventPublisher);
	}

	@Test
	@DisplayName("이 차수의 후보가 아니면 수동 확정은 404이고 상태가 바뀌지 않는다")
	void confirmManuallyUnknownCandidate() {
		givenHost();
		round.openVoting(DEADLINE);
		given(placeCandidateRepository.findByIdAndRoundId(999L, ROUND_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.confirmManually(ROUND_ID, HOST_ID, 999L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PLACE_CANDIDATE_NOT_FOUND);
		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.VOTING);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("이미 확정된 차수는 수동 확정할 수 없다(409)")
	void confirmManuallyAlreadyConfirmed() {
		givenHost();
		PlaceCandidate candidate = PlaceServiceFixture.candidate(round, 500L, "고깃집");
		round.confirmDirectly(candidate);
		given(placeCandidateRepository.findByIdAndRoundId(500L, ROUND_ID)).willReturn(Optional.of(candidate));

		assertThatThrownBy(() -> service.confirmManually(ROUND_ID, HOST_ID, 500L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("호스트 선택 대기 중 재투표를 열면 표를 지우고 새 마감으로 VOTING이 되며 대기 상태가 풀린다")
	void reopenVotingWhileAwaiting() {
		givenHost();
		round.openVoting(DEADLINE);
		round.markVoteClosed(DEADLINE.plusSeconds(1));

		RoundPlaceResponse response = service.openVoting(ROUND_ID, HOST_ID, DEADLINE.plusDays(1));

		verify(placeVoteRepository).deleteAllByRoundId(ROUND_ID);
		assertThat(response.placeStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(response.placeVoteDeadline()).isEqualTo(DEADLINE.plusDays(1));
		assertThat(response.hostDecisionRequired()).isFalse();
	}

	@Test
	@DisplayName("미정 차수에 투표를 열면 VOTING과 마감 시각이 저장되고 표는 지우지 않는다")
	void openVoting() {
		givenHost();

		RoundPlaceResponse response = service.openVoting(ROUND_ID, HOST_ID, DEADLINE);

		assertThat(response.placeStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(response.placeVoteDeadline()).isEqualTo(DEADLINE);
		verifyNoInteractions(placeVoteRepository);
		verify(eventPublisher).publishEvent(any(PlaceChangedEvent.class));
	}

	@Test
	@DisplayName("확정된 차수에 재투표를 열면 표만 지우고 후보는 남기며 확정을 해제한다")
	void reopenVoting() {
		givenHost();
		PlaceCandidate confirmed = PlaceServiceFixture.candidate(round, 500L, "고깃집");
		round.confirmDirectly(confirmed);
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID)).willReturn(List.of(confirmed));

		RoundPlaceResponse response = service.openVoting(ROUND_ID, HOST_ID, DEADLINE);

		verify(placeVoteRepository).deleteAllByRoundId(ROUND_ID);
		verify(placeCandidateRepository, never()).delete(any());
		assertThat(response.placeStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(response.confirmedCandidateId()).isNull();
		assertThat(response.candidates()).extracting(c -> c.id()).containsExactly(500L);
	}

	@Test
	@DisplayName("이미 투표 중이면 투표를 다시 열 수 없고 표도 지우지 않는다")
	void openVotingWhileVoting() {
		givenHost();
		round.openVoting(DEADLINE);

		assertThatThrownBy(() -> service.openVoting(ROUND_ID, HOST_ID, DEADLINE.plusDays(1)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		verifyNoInteractions(placeVoteRepository, eventPublisher);
	}
}
