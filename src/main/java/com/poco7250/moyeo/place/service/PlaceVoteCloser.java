package com.poco7250.moyeo.place.service;

import com.poco7250.moyeo.meeting.repository.RoundRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마감이 지난 장소 투표를 주기적으로 찾아 차수별로 마감 처리한다(PRD P-4).
 * 트랜잭션은 차수마다 PlaceVoteCloseProcessor가 따로 열어 한 차수의 실패가 나머지에 번지지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceVoteCloser {

	private final RoundRepository roundRepository;
	private final PlaceVoteCloseProcessor placeVoteCloseProcessor;
	private final Clock clock;

	/**
	 * 마감 처리할 차수를 모두 처리한다.
	 */
	@Scheduled(cron = "${place.vote-closer.cron:*/30 * * * * *}")
	public void closeExpired() {
		roundRepository.findVoteClosableIds(LocalDateTime.now(clock)).forEach(this::closeSafely);
	}

	private void closeSafely(Long roundId) {
		try {
			placeVoteCloseProcessor.close(roundId);
		} catch (ObjectOptimisticLockingFailureException e) {
			// 호스트 수동 확정·재투표나 다른 인스턴스가 먼저 처리했다. 정상 경합이라 재시도하지 않는다
			log.info("투표 마감 처리 중 충돌, 먼저 처리된 변경을 유지: roundId={}", roundId);
		} catch (RuntimeException e) {
			// 처리하지 못한 차수는 다음 주기에 다시 조회된다
			log.error("투표 마감 처리 실패: roundId={}", roundId, e);
		}
	}
}
