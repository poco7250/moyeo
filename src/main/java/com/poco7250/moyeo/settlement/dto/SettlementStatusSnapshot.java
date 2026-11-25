package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.SettlementStatus;

/**
 * 변경 로그에 남기는 정산 상태. 호스트의 확정 되돌리기(Task 075) 전/후 값으로 쓴다.
 *
 * @param status 정산 상태
 * @param revisionNo 마지막으로 확정된 리비전 번호. 되돌려도 바뀌지 않는다
 */
public record SettlementStatusSnapshot(SettlementStatus status, Integer revisionNo) implements ChangeLogSnapshot {
}
