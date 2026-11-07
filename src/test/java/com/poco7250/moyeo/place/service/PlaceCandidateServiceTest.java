package com.poco7250.moyeo.place.service;

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
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
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
class PlaceCandidateServiceTest {

	private static final PlaceInput INPUT = new PlaceInput("강남 볼링", "강남대로 1", null);
	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	PlaceCandidateRepository placeCandidateRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	ApplicationEventPublisher eventPublisher;

	private PlaceCandidateService service;
	private Round round;

	@BeforeEach
	void setUp() {
		service = new PlaceCandidateService(meetingAccessChecker, placeCandidateRepository, memberRepository,
				new RoundPlaceSnapshot(placeCandidateRepository, eventPublisher), TestClocks.fixed(DEADLINE));
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

	private void assertInvalidTransitionWithoutSave() {
		assertThatThrownBy(() -> service.add(ROUND_ID, MEMBER_ID, INPUT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
		verify(placeCandidateRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}
}
