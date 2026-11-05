package com.poco7250.moyeo.place.service;

import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEMBER_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.ROUND_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.domain.PlaceVote;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.place.repository.PlaceVoteRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceVoteWriterTest {

	private static final Long CANDIDATE_ID = 500L;
	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	PlaceCandidateRepository placeCandidateRepository;

	@Mock
	PlaceVoteRepository placeVoteRepository;

	@Mock
	MemberRepository memberRepository;

	private PlaceVoteWriter writer;
	private Round round;
	private PlaceCandidate candidate;

	@BeforeEach
	void setUp() {
		writer = writerAt(DEADLINE);
		round = PlaceServiceFixture.round();
		candidate = PlaceServiceFixture.candidate(round, CANDIDATE_ID, "고깃집");
	}

	private PlaceVoteWriter writerAt(LocalDateTime now) {
		return new PlaceVoteWriter(meetingAccessChecker, placeCandidateRepository, placeVoteRepository,
				memberRepository, TestClocks.fixed(now));
	}

	private void givenOpenRoundWithCandidate() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID)).willReturn(round);
		round.openVoting(DEADLINE);
		given(placeCandidateRepository.findByIdAndRoundId(CANDIDATE_ID, ROUND_ID)).willReturn(Optional.of(candidate));
	}

	private void assertErrorCode(ThrowingCallable action, ErrorCode expected) {
		assertThatThrownBy(action)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(expected);
		verify(placeVoteRepository, never()).save(any());
		verify(placeVoteRepository, never()).deleteByCandidateIdAndMemberId(anyLong(), anyLong());
	}

	@Test
	@DisplayName("처음 투표하면 표를 저장하고 true를 돌려준다(마감 정각 포함)")
	void castNewVote() {
		givenOpenRoundWithCandidate();
		given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(PlaceServiceFixture.member(MEMBER_ID));

		boolean changed = writer.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID);

		assertThat(changed).isTrue();
		ArgumentCaptor<PlaceVote> vote = ArgumentCaptor.forClass(PlaceVote.class);
		verify(placeVoteRepository).save(vote.capture());
		assertThat(vote.getValue().getCandidate()).isSameAs(candidate);
		assertThat(vote.getValue().getMember().getId()).isEqualTo(MEMBER_ID);
	}

	@Test
	@DisplayName("같은 후보에 다시 투표하면 저장 없이 false를 돌려준다")
	void castAgain() {
		givenOpenRoundWithCandidate();
		given(placeVoteRepository.existsByCandidateIdAndMemberId(CANDIDATE_ID, MEMBER_ID)).willReturn(true);

		boolean changed = writer.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID);

		assertThat(changed).isFalse();
		verify(placeVoteRepository, never()).save(any());
	}

	@Test
	@DisplayName("표를 취소하면 지우고 true를 돌려준다")
	void cancel() {
		givenOpenRoundWithCandidate();
		given(placeVoteRepository.deleteByCandidateIdAndMemberId(CANDIDATE_ID, MEMBER_ID)).willReturn(1);

		assertThat(writer.cancel(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).isTrue();
	}

	@Test
	@DisplayName("표가 없는데 취소하면 false를 돌려준다")
	void cancelWithoutVote() {
		givenOpenRoundWithCandidate();
		given(placeVoteRepository.deleteByCandidateIdAndMemberId(CANDIDATE_ID, MEMBER_ID)).willReturn(0);

		assertThat(writer.cancel(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).isFalse();
	}

	@Test
	@DisplayName("비멤버는 투표할 수 없다(403)")
	void nonMember() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertErrorCode(() -> writer.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID), ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(placeCandidateRepository);
	}

	@Test
	@DisplayName("투표 중이 아닌 차수에는 투표할 수 없다(409)")
	void notVoting() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID)).willReturn(round);

		assertErrorCode(() -> writer.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID),
				ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
	}

	@Test
	@DisplayName("이 차수의 후보가 아니면 404다")
	void candidateOfOtherRound() {
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID)).willReturn(round);
		round.openVoting(DEADLINE);
		given(placeCandidateRepository.findByIdAndRoundId(CANDIDATE_ID, ROUND_ID)).willReturn(Optional.empty());

		assertErrorCode(() -> writer.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID), ErrorCode.PLACE_CANDIDATE_NOT_FOUND);
	}

	@Test
	@DisplayName("마감이 지나면 투표도 취소도 할 수 없다(409)")
	void deadlinePassed() {
		PlaceVoteWriter lateWriter = writerAt(DEADLINE.plusSeconds(1));
		given(meetingAccessChecker.requireRoundMember(ROUND_ID, MEMBER_ID)).willReturn(round);
		round.openVoting(DEADLINE);

		assertErrorCode(() -> lateWriter.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID),
				ErrorCode.PLACE_VOTE_DEADLINE_PASSED);
		assertErrorCode(() -> lateWriter.cancel(ROUND_ID, CANDIDATE_ID, MEMBER_ID),
				ErrorCode.PLACE_VOTE_DEADLINE_PASSED);
	}
}
