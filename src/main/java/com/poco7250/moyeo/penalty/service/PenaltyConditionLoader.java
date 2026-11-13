package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.global.auth.GameAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 벌칙 조건을 바꾸기 전 공통 검사를 하고 대상 벌칙을 읽는다. 조건 쓰기 Service가 진입 시 쓴다.
 * 정산을 잠그므로 호출자 트랜잭션 안에서만 쓴다.
 */
@Component
@RequiredArgsConstructor
public class PenaltyConditionLoader {

	private final GameAccessChecker gameAccessChecker;
	private final SettlementGuard settlementGuard;
	private final PenaltyRepository penaltyRepository;

	/**
	 * 호스트 권한과 게임 상태를 검사하고, 정산을 잠근 뒤 게임의 비무효 벌칙을 읽는다.
	 * 잠근 뒤에 읽어야 동시에 들어온 조건 변경·결과 공개와 순서가 갈린다.
	 *
	 * @throws BusinessException 그룹 멤버가 아니면 NOT_GROUP_MEMBER, 호스트가 아니면 NOT_MEETING_HOST,
	 *         금액형이 아니거나 공개 전·취소된 게임이거나 벌칙이 전부 무효면 PENALTY_NOT_CONFIGURABLE
	 */
	PenaltyConditionTarget loadForHost(Long gameId, Long memberId) {
		GameSession game = gameAccessChecker.requireHost(gameId, memberId);
		if (game.getPenaltyKind() != PenaltyKind.MONEY || !game.isRevealed()) {
			throw new BusinessException(ErrorCode.PENALTY_NOT_CONFIGURABLE);
		}
		Long meetingId = game.getRound().getMeeting().getId();
		settlementGuard.lockForAmountChange(meetingId);
		List<Penalty> penalties = penaltyRepository
				.findAllByGameSessionIdAndStatusNot(gameId, PenaltyStatus.VOIDED).stream()
				.sorted(Comparator.comparing(Penalty::getId))
				.toList();
		if (penalties.isEmpty()) {
			throw new BusinessException(ErrorCode.PENALTY_NOT_CONFIGURABLE);
		}
		return new PenaltyConditionTarget(game, meetingId, penalties);
	}
}
