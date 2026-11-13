package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyConditionResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleResponse;
import com.poco7250.moyeo.penalty.dto.PenaltyWaiverSnapshot;
import java.util.List;

/**
 * 조건을 바꿀 게임과 그 게임의 비무효 벌칙. 조건 설정과 면제는 게임 단위로 전원에게 함께 적용된다.
 *
 * @param penalties 비무효 벌칙(id 오름차순, 1건 이상)
 */
record PenaltyConditionTarget(GameSession game, Long meetingId, List<Penalty> penalties) {

	Long gameId() {
		return game.getId();
	}

	/**
	 * 전원이 함께 가진 상태. 가장 먼저 생긴 벌칙 하나를 기준으로 읽는다(InheritedCondition과 같은 기준).
	 */
	PenaltyStatus status() {
		return first().getStatus();
	}

	List<Long> memberIds() {
		return penalties.stream().map(penalty -> penalty.getMember().getId()).toList();
	}

	/**
	 * 지금 기록된 면제 정보로 면제 상태 스냅샷을 만든다.
	 */
	PenaltyWaiverSnapshot waivedSnapshot() {
		return PenaltyWaiverSnapshot.waived(gameId(), memberIds(), first().getWaivedBy(), first().getWaivedAt());
	}

	PenaltyWaiverSnapshot notWaivedSnapshot() {
		return PenaltyWaiverSnapshot.notWaived(gameId(), memberIds());
	}

	/**
	 * 현재 상태를 응답으로 바꾼다.
	 *
	 * @param rule 게임에 걸린 금액 조건. 없으면 null
	 */
	PenaltyConditionResponse toResponse(PenaltyRule rule) {
		return new PenaltyConditionResponse(gameId(), status(), rule == null ? null : PenaltyRuleResponse.from(rule),
				first().getWaivedBy(), first().getWaivedAt(), memberIds());
	}

	private Penalty first() {
		return penalties.get(0);
	}
}
