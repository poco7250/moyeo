package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.Expense;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 지출 저장소.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

	/**
	 * 지출을 차수와 함께 조회한다(인가 검사에 모임 id가 필요하다).
	 */
	@Query("select e from Expense e join fetch e.round where e.id = :id")
	Optional<Expense> findWithRoundById(@Param("id") Long id);

	/**
	 * 모임의 지출을 차수·분배 조정과 함께 차수 순, 등록 순으로 조회한다(N+1 방지).
	 */
	@Query("select e from Expense e join fetch e.round r left join fetch e.shares "
			+ "where r.meeting.id = :meetingId order by r.seq, e.id")
	List<Expense> findAllWithSharesByMeetingId(@Param("meetingId") Long meetingId);

	/**
	 * 주어진 차수 중 지출이 1건 이상 있는 차수의 id를 조회한다.
	 */
	@Query("select distinct e.round.id from Expense e where e.round.id in :roundIds")
	Set<Long> findRoundIdsWithExpense(@Param("roundIds") Collection<Long> roundIds);
}
