package com.poco7250.moyeo.place.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class PlaceVoteCloserTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Mock
	RoundRepository roundRepository;

	@Mock
	PlaceVoteCloseProcessor placeVoteCloseProcessor;

	private PlaceVoteCloser closer;

	@BeforeEach
	void setUp() {
		closer = new PlaceVoteCloser(roundRepository, placeVoteCloseProcessor, TestClocks.fixed(NOW));
		given(roundRepository.findVoteClosableIds(NOW)).willReturn(List.of(1L, 2L, 3L));
	}

	@Test
	@DisplayName("주입한 Clock 기준으로 찾은 차수를 모두 처리한다")
	void closesAll() {
		closer.closeExpired();

		verify(placeVoteCloseProcessor).close(1L);
		verify(placeVoteCloseProcessor).close(2L);
		verify(placeVoteCloseProcessor).close(3L);
	}

	@Test
	@DisplayName("한 차수에서 낙관적 락 충돌이나 예외가 나도 다음 차수를 처리한다")
	void continuesAfterFailure() {
		willThrow(new ObjectOptimisticLockingFailureException(Round.class, 1L))
				.given(placeVoteCloseProcessor).close(1L);
		willThrow(new IllegalStateException("장애")).given(placeVoteCloseProcessor).close(2L);

		closer.closeExpired();

		verify(placeVoteCloseProcessor).close(3L);
	}
}
