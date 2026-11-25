package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 새 벌칙자가 원 회차에서 이어받을 상태. 면제(WAIVED)만 실제로 이어받는다. 최초 공개면 ASSIGNED(이어받을 것 없음)다.
 *
 * @param waivedBy 면제 상태를 이어받을 때의 원래 면제자
 * @param waivedAt 면제 상태를 이어받을 때의 원래 면제 시각
 */
record InheritedCondition(PenaltyStatus status, Long waivedBy, LocalDateTime waivedAt) {

	static final InheritedCondition UNSET = new InheritedCondition(PenaltyStatus.ASSIGNED, null, null);

	/**
	 * 원 회차의 비무효 벌칙에서 조건 상태를 읽는다.
	 * 면제는 게임 단위로 전원에게 적용되므로 가장 먼저 생긴 벌칙 하나를 기준으로 삼는다.
	 */
	static InheritedCondition from(List<Penalty> activeOriginals) {
		return activeOriginals.stream()
				.min(Comparator.comparing(Penalty::getId))
				.map(penalty -> new InheritedCondition(penalty.getStatus(), penalty.getWaivedBy(), penalty.getWaivedAt()))
				.orElse(UNSET);
	}

	/**
	 * 새 벌칙을 만들고, 원 회차가 면제(WAIVED)였으면 면제 상태를 이어받게 한다.
	 * 금액형이면 새 벌칙은 APPLIED로 생기므로 면제만 이어받으면 된다.
	 */
	Penalty newPenalty(GameSession game, Member member) {
		Penalty penalty = Penalty.assign(game, member);
		if (status == PenaltyStatus.WAIVED && penalty.getStatus() == PenaltyStatus.APPLIED) {
			penalty.waive(waivedBy, waivedAt);
		}
		return penalty;
	}
}
