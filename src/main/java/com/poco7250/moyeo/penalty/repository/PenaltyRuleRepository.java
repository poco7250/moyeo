package com.poco7250.moyeo.penalty.repository;

import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 벌칙 금액 조건 저장소.
 */
public interface PenaltyRuleRepository extends JpaRepository<PenaltyRule, Long> {

	/**
	 * 게임의 금액 조건을 조회한다(게임당 최대 1건).
	 */
	Optional<PenaltyRule> findByGameSessionId(Long gameSessionId);

	/**
	 * 주어진 지출을 대상으로 한 금액 조건을 모두 조회한다(지출 삭제 시 함께 정리할 대상).
	 */
	List<PenaltyRule> findAllByTargetExpenseId(Long targetExpenseId);

	/**
	 * 주어진 게임들의 금액 조건을 대상 지출·그 차수와 함께 조회한다(N+1 방지).
	 */
	@Query("select pr from PenaltyRule pr join fetch pr.targetExpense e join fetch e.round "
			+ "where pr.gameSession.id in :gameIds")
	List<PenaltyRule> findAllWithTargetByGameSessionIdIn(@Param("gameIds") Collection<Long> gameIds);
}
