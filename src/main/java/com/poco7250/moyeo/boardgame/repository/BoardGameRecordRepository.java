package com.poco7250.moyeo.boardgame.repository;

import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보드게임 기록 저장소.
 */
public interface BoardGameRecordRepository extends JpaRepository<BoardGameRecord, Long> {

	/**
	 * 차수의 보드게임 기록을 최근 순으로 순위와 함께 읽는다.
	 */
	@EntityGraph(attributePaths = "ranks")
	List<BoardGameRecord> findAllByRoundIdOrderByIdDesc(Long roundId);

	/**
	 * 여러 차수의 보드게임 기록을 기록 순으로 순위와 함께 읽는다(모임 히스토리).
	 */
	@EntityGraph(attributePaths = "ranks")
	List<BoardGameRecord> findAllByRoundIdInOrderByIdAsc(Collection<Long> roundIds);
}
