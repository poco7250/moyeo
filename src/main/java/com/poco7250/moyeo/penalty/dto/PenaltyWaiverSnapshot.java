package com.poco7250.moyeo.penalty.dto;

import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 변경 로그에 남기는 게임 단위 면제 상태. 면제·면제 취소의 전/후 값으로 쓴다.
 * 금액 조건의 설정·해제는 따로 PenaltyRuleSnapshot으로 남기므로 여기 상태는 ASSIGNED(면제 아님) 아니면 WAIVED다.
 *
 * @param waivedBy 면제한 회원 id. 면제가 아니면 null
 * @param waivedAt 면제한 시각. 면제가 아니면 null
 * @param penaltyMemberIds 함께 바뀐 벌칙자 회원 id
 */
public record PenaltyWaiverSnapshot(Long gameSessionId, PenaltyStatus status, Long waivedBy, LocalDateTime waivedAt,
		List<Long> penaltyMemberIds) implements ChangeLogSnapshot {

	/**
	 * 면제된 상태의 스냅샷을 만든다.
	 */
	public static PenaltyWaiverSnapshot waived(Long gameSessionId, List<Long> penaltyMemberIds, Long waivedBy,
			LocalDateTime waivedAt) {
		return new PenaltyWaiverSnapshot(gameSessionId, PenaltyStatus.WAIVED, waivedBy, waivedAt, penaltyMemberIds);
	}

	/**
	 * 면제가 아닌 상태의 스냅샷을 만든다.
	 */
	public static PenaltyWaiverSnapshot notWaived(Long gameSessionId, List<Long> penaltyMemberIds) {
		return new PenaltyWaiverSnapshot(gameSessionId, PenaltyStatus.ASSIGNED, null, null, penaltyMemberIds);
	}
}
