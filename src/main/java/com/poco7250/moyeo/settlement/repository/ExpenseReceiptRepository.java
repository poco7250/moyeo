package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.ExpenseReceipt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 지출 영수증 사진 저장소.
 */
public interface ExpenseReceiptRepository extends JpaRepository<ExpenseReceipt, Long> {

	/**
	 * 지출의 영수증 사진을 조회한다.
	 */
	@Query("select r from ExpenseReceipt r where r.expense.id = :expenseId")
	Optional<ExpenseReceipt> findByExpenseId(@Param("expenseId") Long expenseId);

	/**
	 * 여러 지출의 영수증 사진을 한 번에 조회한다(지출 목록 N+1 방지).
	 */
	@Query("select r from ExpenseReceipt r where r.expense.id in :expenseIds")
	List<ExpenseReceipt> findAllByExpenseIdIn(@Param("expenseIds") Collection<Long> expenseIds);
}
