package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 지출 저장소.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
}
