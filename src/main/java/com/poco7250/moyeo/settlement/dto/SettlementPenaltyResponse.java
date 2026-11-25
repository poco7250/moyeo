package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 정산 탭의 벌칙 반영 내역 1건(PRD S-4). "민수: 사다리 꽝 → +10,000원" 같은 문구는 이 값으로 화면에서 만든다.
 * APPLIED는 정산 반영(chargeAmount), WAIVED는 면제(waivedBy·waivedAt), ASSIGNED는 금액 없는 옛 게임 벌칙이다.
 * 게스트 벌칙(guest)은 정산에 들어가지 않고 "앱 밖에서 내기"로 표시만 한다. VOIDED는 싣지 않는다.
 *
 * @param game              벌칙 출처 게임(금액 포함)
 * @param guest             벌칙자가 모임 게스트인지
 * @param chargeAmount      정산에 반영된 부담액(원). 정산에 들어가지 않으면 null, 나눠 받을 사람이 없으면 0
 * @param creditedMemberIds 이 게임 벌칙금을 나눠 받는 사람 회원 id(오름차순)
 * @param waivedBy          면제한 회원 id. WAIVED가 아니면 null
 * @param waivedAt          면제한 시각. WAIVED가 아니면 null
 */
public record SettlementPenaltyResponse(
		Long penaltyId,
		Long memberId,
		PenaltyStatus status,
		Game game,
		boolean guest,
		Long chargeAmount,
		List<Long> creditedMemberIds,
		Long waivedBy,
		LocalDateTime waivedAt) {

	/**
	 * 벌칙과 계산 결과로 내역을 만든다.
	 *
	 * @param chargeAmount      계산된 부담액. 계산에 들어가지 않았으면 null
	 * @param creditedMemberIds 나눠 받는 사람. 없으면 null도 된다
	 */
	public static SettlementPenaltyResponse of(Penalty penalty, Long chargeAmount, Set<Long> creditedMemberIds) {
		return new SettlementPenaltyResponse(penalty.getId(), penalty.getMember().getId(), penalty.getStatus(),
				Game.from(penalty.getGameSession()), penalty.getMember().isGuest(), chargeAmount,
				creditedMemberIds == null ? List.of() : List.copyOf(creditedMemberIds),
				penalty.getWaivedBy(), penalty.getWaivedAt());
	}

	/**
	 * 벌칙 출처 게임.
	 *
	 * @param penaltyAmount 벌칙자 1인 금액(원). 금액 없는 옛 게임이면 null
	 */
	public record Game(Long gameId, GameType type, Long penaltyAmount) {

		static Game from(GameSession game) {
			return new Game(game.getId(), game.getType(), game.getPenaltyAmount());
		}
	}
}
