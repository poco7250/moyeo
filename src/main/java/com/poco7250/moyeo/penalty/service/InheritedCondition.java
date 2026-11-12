package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 새 벌칙자가 이어받을 조건 상태. 최초 공개면 조건이 없는 ASSIGNED다.
 *
 * @param waivedBy 면제 상태를 이어받을 때의 원래 면제자
 * @param waivedAt 면제 상태를 이어받을 때의 원래 면제 시각
 */
record InheritedCondition(PenaltyStatus status, Long waivedBy, LocalDateTime waivedAt) {

	static final InheritedCondition UNSET = new InheritedCondition(PenaltyStatus.ASSIGNED, null, null);

	/**
	 * 원 회차의 비무효 벌칙에서 조건 상태를 읽는다.
	 * 조건 설정과 면제는 게임 단위로 전원에게 적용되므로 가장 먼저 생긴 벌칙 하나를 기준으로 삼는다.
	 */
	static InheritedCondition from(List<Penalty> activeOriginals) {
		return activeOriginals.stream()
				.min(Comparator.comparing(Penalty::getId))
				.map(penalty -> new InheritedCondition(penalty.getStatus(), penalty.getWaivedBy(), penalty.getWaivedAt()))
				.orElse(UNSET);
	}

	/**
	 * 이 조건 상태를 가진 새 벌칙을 만든다.
	 */
	Penalty newPenalty(GameSession game, Member member) {
		Penalty penalty = Penalty.assign(game, member);
		if (status == PenaltyStatus.APPLIED) {
			penalty.apply();
		} else if (status == PenaltyStatus.WAIVED) {
			penalty.waive(waivedBy, waivedAt);
		}
		return penalty;
	}
}
