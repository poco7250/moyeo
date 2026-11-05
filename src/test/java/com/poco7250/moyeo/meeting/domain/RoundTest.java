package com.poco7250.moyeo.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class RoundTest {

	private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 12, 19, 18, 0);

	private Meeting meeting;
	private Round round;
	private PlaceCandidate candidate;

	@BeforeEach
	void setUp() {
		meeting = Meeting.create(Group.create("대학 동기"), "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L);
		round = Round.create(meeting, 1, "밥");
		candidate = candidateOf(round);
	}

	private PlaceCandidate candidateOf(Round target) {
		return PlaceCandidate.propose(target, Place.of("고깃집", null, null), Member.ofKakao(1L, "모여", null, null));
	}

	private void assertInvalidTransition(Executable action) {
		assertThatThrownBy(action::execute)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.ROUND_PLACE_INVALID_TRANSITION);
	}

	@Test
	@DisplayName("미정 차수를 바로 확정하면 CONFIRMED가 되고 확정 후보가 설정된다")
	void confirmDirectly() {
		round.confirmDirectly(candidate);

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(round.getConfirmedCandidate()).isSameAs(candidate);
	}

	@Test
	@DisplayName("미정 차수에 투표를 열면 VOTING이 되고 마감 시각이 저장된다")
	void openVotingFromUndecided() {
		round.openVoting(DEADLINE);

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(round.getPlaceVoteDeadline()).isEqualTo(DEADLINE);
	}

	@Test
	@DisplayName("투표 중인 차수를 확정하면 CONFIRMED가 된다")
	void confirmFromVoting() {
		round.openVoting(DEADLINE);

		round.confirm(candidate);

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.CONFIRMED);
		assertThat(round.getConfirmedCandidate()).isSameAs(candidate);
	}

	@Test
	@DisplayName("확정된 차수에 재투표를 열면 VOTING이 되고 이전 확정이 해제된다")
	void reopenVoting() {
		round.confirmDirectly(candidate);

		round.openVoting(DEADLINE);

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.VOTING);
		assertThat(round.getConfirmedCandidate()).isNull();
		assertThat(round.getPlaceVoteDeadline()).isEqualTo(DEADLINE);
	}

	@Test
	@DisplayName("미정 차수는 투표 확정할 수 없다")
	void confirmFromUndecidedFails() {
		assertInvalidTransition(() -> round.confirm(candidate));
		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.UNDECIDED);
	}

	@Test
	@DisplayName("투표 중인 차수는 바로 확정하거나 투표를 다시 열 수 없다")
	void votingTransitionsFail() {
		round.openVoting(DEADLINE);

		assertInvalidTransition(() -> round.confirmDirectly(candidate));
		assertInvalidTransition(() -> round.openVoting(DEADLINE.plusDays(1)));
		assertThat(round.getPlaceVoteDeadline()).isEqualTo(DEADLINE);
	}

	@Test
	@DisplayName("확정된 차수는 다시 바로 확정하거나 투표 확정할 수 없다")
	void confirmedTransitionsFail() {
		round.confirmDirectly(candidate);

		assertInvalidTransition(() -> round.confirmDirectly(candidate));
		assertInvalidTransition(() -> round.confirm(candidate));
	}

	@Test
	@DisplayName("투표 중이면 마감 정각까지 표를 바꿀 수 있고 1초 뒤부터는 마감 에러다")
	void requireVoteOpenDeadline() {
		round.openVoting(DEADLINE);

		assertThatCode(() -> round.requireVoteOpen(DEADLINE)).doesNotThrowAnyException();
		assertThatThrownBy(() -> round.requireVoteOpen(DEADLINE.plusSeconds(1)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PLACE_VOTE_DEADLINE_PASSED);
	}

	@Test
	@DisplayName("투표 중이 아니면 마감 전이어도 표를 바꿀 수 없다")
	void requireVoteOpenNotVoting() {
		assertInvalidTransition(() -> round.requireVoteOpen(DEADLINE));

		round.confirmDirectly(candidate);

		assertInvalidTransition(() -> round.requireVoteOpen(DEADLINE));
	}

	@Test
	@DisplayName("다른 차수의 후보로는 확정할 수 없다")
	void otherRoundCandidateFails() {
		PlaceCandidate otherCandidate = candidateOf(Round.create(meeting, 2, "볼링"));

		assertThatThrownBy(() -> round.confirmDirectly(otherCandidate))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.UNDECIDED);
		assertThat(round.getConfirmedCandidate()).isNull();
	}
}
