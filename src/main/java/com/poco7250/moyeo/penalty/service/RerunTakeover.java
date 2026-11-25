package com.poco7250.moyeo.penalty.service;

import java.util.List;

/**
 * 다시 돌린 회차가 공개되면서 원 회차에서 넘겨받은 것. 최초 공개면 넘겨받은 게 없다.
 *
 * @param rerunOfId 원 회차 id. 최초 공개면 null
 * @param condition 새 벌칙자가 이어받을 조건 상태
 * @param voidedMemberIds 무효가 된 원 회차 벌칙자 회원 id(오름차순)
 * @param voidedMemberPenalty 무효가 된 원 회차 벌칙 중 그룹 멤버(게스트 아님) 벌칙이 있었는지
 */
record RerunTakeover(Long rerunOfId, InheritedCondition condition, List<Long> voidedMemberIds,
		boolean voidedMemberPenalty) {

	static final RerunTakeover NONE = new RerunTakeover(null, InheritedCondition.UNSET, List.of(), false);

	boolean isRerun() {
		return rerunOfId != null;
	}
}
