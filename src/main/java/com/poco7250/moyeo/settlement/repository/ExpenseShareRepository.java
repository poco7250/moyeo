package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 지출 분배 조정 저장소.
 */
public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
}
