package com.poco7250.moyeo.place.service;

import static com.poco7250.moyeo.place.service.PlaceServiceFixture.MEMBER_ID;
import static com.poco7250.moyeo.place.service.PlaceServiceFixture.ROUND_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.place.dto.RoundVotesResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PlaceVoteServiceTest {

	private static final Long CANDIDATE_ID = 500L;
	private static final RoundVotesResponse RESPONSE = new RoundVotesResponse(ROUND_ID, List.of());

	@Mock
	PlaceVoteWriter placeVoteWriter;

	@Mock
	PlaceVoteTally placeVoteTally;

	@InjectMocks
	PlaceVoteService service;

	@Test
	@DisplayName("표가 새로 저장되면 현황을 발행한다")
	void votePublishes() {
		given(placeVoteWriter.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).willReturn(true);
		given(placeVoteTally.publish(ROUND_ID)).willReturn(RESPONSE);

		assertThat(service.vote(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).isSameAs(RESPONSE);
		verify(placeVoteTally, never()).read(anyLong(), anyLong());
	}

	@Test
	@DisplayName("이미 투표했으면 발행 없이 현황만 돌려준다")
	void voteAgainDoesNotPublish() {
		given(placeVoteWriter.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).willReturn(false);
		given(placeVoteTally.read(ROUND_ID, MEMBER_ID)).willReturn(RESPONSE);

		assertThat(service.vote(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).isSameAs(RESPONSE);
		verify(placeVoteTally, never()).publish(anyLong());
	}

	@Test
	@DisplayName("동시 투표로 유니크 위반이 나면 재시도하고, 상대가 저장한 표라 발행하지 않는다")
	void retryOnUniqueViolation() {
		given(placeVoteWriter.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID))
				.willThrow(new DataIntegrityViolationException("중복"))
				.willReturn(false);
		given(placeVoteTally.read(ROUND_ID, MEMBER_ID)).willReturn(RESPONSE);

		assertThat(service.vote(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).isSameAs(RESPONSE);
		verify(placeVoteWriter, times(2)).cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID);
		verify(placeVoteTally, never()).publish(anyLong());
	}

	@Test
	@DisplayName("재시도도 실패하면 예외를 그대로 던진다")
	void retryFails() {
		given(placeVoteWriter.cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID))
				.willThrow(new DataIntegrityViolationException("중복"));

		assertThatThrownBy(() -> service.vote(ROUND_ID, CANDIDATE_ID, MEMBER_ID))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(placeVoteWriter, times(2)).cast(ROUND_ID, CANDIDATE_ID, MEMBER_ID);
		verifyNoInteractions(placeVoteTally);
	}

	@Test
	@DisplayName("표가 지워지면 현황을 발행한다")
	void cancelPublishes() {
		given(placeVoteWriter.cancel(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).willReturn(true);
		given(placeVoteTally.publish(ROUND_ID)).willReturn(RESPONSE);

		assertThat(service.cancel(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).isSameAs(RESPONSE);
	}

	@Test
	@DisplayName("지울 표가 없으면 발행 없이 현황만 돌려준다")
	void cancelWithoutVoteDoesNotPublish() {
		given(placeVoteWriter.cancel(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).willReturn(false);
		given(placeVoteTally.read(ROUND_ID, MEMBER_ID)).willReturn(RESPONSE);

		assertThat(service.cancel(ROUND_ID, CANDIDATE_ID, MEMBER_ID)).isSameAs(RESPONSE);
		verify(placeVoteTally, never()).publish(anyLong());
	}
}
