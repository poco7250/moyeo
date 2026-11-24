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

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.dto.CandidateResponse;
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.dto.RoundPlaceResponse;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceRouletteRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import com.poco7250.moyeo.support.TestClocks;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
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
class PlaceCandidateServiceTest {

	private static final PlaceInput INPUT = new PlaceInput("강남 볼링", "강남대로 1", null);
	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	PlaceCandidateRepository placeCandidateRepository;

	@Mock
	PlaceVoteRepository placeVoteRepository;

	@Mock
	PlaceRouletteRepository placeRouletteRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	EntityManager entityManager;

	@Mock
	ApplicationEventPublisher eventPublisher;

	private PlaceCandidateService service;
	private Round round;

	@BeforeEach
	void setUp() {
		service = new PlaceCandidateService(meetingAccessChecker, placeCandidateRepository, placeVoteRepository,
				placeRouletteRepository, memberRepository, new RoundPlaceSnapshot(placeCandidateRepository, eventPublisher),
				entityManager, TestClocks.fixed(DEADLINE));
		round = PlaceServiceFixture.round();
	}

	private void givenMember() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID)).willReturn(round);
	}

	@Test
	@DisplayName("투표 중인 차수에 후보를 추가하면 저장하고 PLACE_CHANGED를 발행한다")
	void add() {
		givenMember();
		// 마감 정각까지는 추가할 수 있다
		round.openVoting(DEADLINE);
		given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(PlaceServiceFixture.member(MEMBER_ID));
		given(placeCandidateRepository.save(any(PlaceCandidate.class))).willAnswer(invocation -> {
			PlaceCandidate saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 500L);
			return saved;
		});
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID))
				.willAnswer(invocation -> List.of(PlaceServiceFixture.candidate(round, 500L, "강남 볼링")));

		CandidateResponse response = service.add(ROUND_ID, MEMBER_ID, INPUT);

		assertThat(response.id()).isEqualTo(500L);
		assertThat(response.label()).isEqualTo("강남 볼링");
		assertThat(response.mapUrl()).startsWith("https://map.kakao.com/link/search/");
		assertThat(response.proposedByMemberId()).isEqualTo(MEMBER_ID);
		ArgumentCaptor<PlaceChangedEvent> event = ArgumentCaptor.forClass(PlaceChangedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().meetingId()).isEqualTo(MEETING_ID);
		assertThat(event.getValue().data().placeStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(event.getValue().data().candidates()).extracting(CandidateResponse::id).containsExactly(500L);
	}

	@Test
	@DisplayName("장소 미정 차수에는 후보를 추가할 수 없다(409, 저장 없음)")
	void undecidedFails() {
		givenMember();
		assertInvalidTransitionWithoutSave();
	}

	@Test
	@DisplayName("확정된 차수에는 후보를 추가할 수 없다(409, 저장 없음)")
	void confirmedFails() {
		givenMember();
		round.confirmDirectly(PlaceServiceFixture.candidate(round, 1L, "고깃집"));

		assertInvalidTransitionWithoutSave();
	}

	@Test
	@DisplayName("마감이 지난 차수에는 후보를 추가할 수 없다(409, 저장 없음)")
	void deadlinePassedFails() {
		givenMember();
		round.openVoting(DEADLINE.minusSeconds(1));

		assertThatThrownBy(() -> service.add(ROUND_ID, MEMBER_ID, INPUT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PLACE_VOTE_DEADLINE_PASSED);
		verify(placeCandidateRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("비멤버는 후보를 추가할 수 없다(403, 저장 없음)")
	void nonMember() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertThatThrownBy(() -> service.add(ROUND_ID, MEMBER_ID, INPUT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(placeCandidateRepository, eventPublisher);
	}

	@Test
	@DisplayName("호스트가 투표 중인 차수의 후보를 지우면 표·후보를 지우고 차수 version을 올린 뒤 PLACE_CHANGED를 발행한다")
	void delete() {
		givenHost();
		round.openVoting(DEADLINE);
		PlaceCandidate target = PlaceServiceFixture.candidate(round, 501L, "잘못 넣은 곳");
		given(placeCandidateRepository.findByIdAndRoundId(501L, ROUND_ID)).willReturn(Optional.of(target));
		given(placeCandidateRepository.findAllByRoundIdOrderById(ROUND_ID))
				.willReturn(List.of(PlaceServiceFixture.candidate(round, 500L, "강남 볼링")));

		RoundPlaceResponse response = service.delete(ROUND_ID, HOST_ID, 501L);

		verify(entityManager).lock(round, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
		verify(placeVoteRepository).deleteAllByCandidateId(501L);
		verify(placeCandidateRepository).delete(target);
		assertThat(response.candidates()).extracting(CandidateResponse::id).containsExactly(500L);
		ArgumentCaptor<PlaceChangedEvent> event = ArgumentCaptor.forClass(PlaceChangedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().data().candidates()).extracting(CandidateResponse::id).containsExactly(500L);
	}

	@Test
	@DisplayName("마감이 지나 호스트 선택을 기다리는 차수에서도 후보를 지울 수 있다")
	void deleteAfterDeadline() {
		givenHost();
		round.openVoting(DEADLINE.minusSeconds(1));
		PlaceCandidate target = PlaceServiceFixture.candidate(round, 501L, "잘못 넣은 곳");
		given(placeCandidateRepository.findByIdAndRoundId(501L, ROUND_ID)).willReturn(Optional.of(target));

		service.delete(ROUND_ID, HOST_ID, 501L);

		verify(placeCandidateRepository).delete(target);
	}

	@Test
	@DisplayName("투표 중이 아닌 차수의 후보는 지울 수 없다(409, 삭제 없음)")
	void deleteNotVoting() {
		givenHost();
		round.confirmDirectly(PlaceServiceFixture.candidate(round, 501L, "고깃집"));

		assertDeleteFails(501L, ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
	}

	@Test
	@DisplayName("이 차수의 후보가 아니면 404, 룰렛 기록에 남은 후보면 409(둘 다 삭제 없음)")
	void deleteMissingOrInRoulette() {
		givenHost();
		round.openVoting(DEADLINE);
		given(placeCandidateRepository.findByIdAndRoundId(999L, ROUND_ID)).willReturn(Optional.empty());
		given(placeCandidateRepository.findByIdAndRoundId(501L, ROUND_ID))
				.willReturn(Optional.of(PlaceServiceFixture.candidate(round, 501L, "고깃집")));
		given(placeRouletteRepository.existsByCandidateId(501L)).willReturn(true);

		assertDeleteFails(999L, ErrorCode.PLACE_CANDIDATE_NOT_FOUND);
		assertDeleteFails(501L, ErrorCode.PLACE_CANDIDATE_IN_ROULETTE);
	}

	@Test
	@DisplayName("호스트가 아니면 후보를 지울 수 없다(403, 삭제 없음)")
	void deleteByNonHost() {
		given(meetingAccessChecker.requireRoundHost(ROUND_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_MEETING_HOST));

		assertThatThrownBy(() -> service.delete(ROUND_ID, MEMBER_ID, 501L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_MEETING_HOST);
		verifyNoInteractions(placeCandidateRepository, placeVoteRepository, entityManager, eventPublisher);
	}

	private void givenHost() {
		given(meetingAccessChecker.requireRoundHost(ROUND_ID, HOST_ID)).willReturn(round);
	}

	private void assertDeleteFails(Long candidateId, ErrorCode errorCode) {
		assertThatThrownBy(() -> service.delete(ROUND_ID, HOST_ID, candidateId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
		verify(placeCandidateRepository, never()).delete(any());
		verifyNoInteractions(placeVoteRepository, entityManager, eventPublisher);
	}

	private void assertInvalidTransitionWithoutSave() {
		assertThatThrownBy(() -> service.add(ROUND_ID, MEMBER_ID, INPUT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		verify(placeCandidateRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}
}
