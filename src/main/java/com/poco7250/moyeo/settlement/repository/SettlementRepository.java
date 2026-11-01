package com.poco7250.moyeo.settlement.repository;

import com.poco7250.moyeo.settlement.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 정산 저장소. 직렬화용 잠금 조회는 Task 021에서 추가한다.
 */
public interface SettlementRepository extends JpaRepository<Settlement, Long> {
}
