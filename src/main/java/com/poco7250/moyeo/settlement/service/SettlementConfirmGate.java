package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.calc.SettlementResult;
import com.poco7250.moyeo.settlement.dto.InProgressGameResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정산 확정·재확정 전 검사(PRD S-10). 순서대로 ① 대기·진행 중 금액형 게임 ② 미확인 경고를 보고,
 * 처음 걸린 검사에서 막힌 대상 목록을 에러 상세에 실어 409로 끝낸다. 정산을 잠근 트랜잭션 안에서 불러야 검사 결과가 유지된다.
 */
@Component
@RequiredArgsConstructor
public class SettlementConfirmGate {

	private final GameSessionRepository gameSessionRepository;

	/**
	 * 확정할 수 있는지 검사한다.
	 *
	 * @param result 모임 벌칙까지 반영해 계산한 정산 결과
	 * @throws BusinessException MONEY_GAME_IN_PROGRESS, WARNING_UNCONFIRMED
	 */
	public void check(Long meetingId, SettlementResult result, boolean acknowledgeWarnings) {
		requireNoMoneyGameInProgress(meetingId);
		requireWarningsAcknowledged(result, acknowledgeWarnings);
	}

	private void requireNoMoneyGameInProgress(Long meetingId) {
		List<InProgressGameResponse> games = gameSessionRepository.findAllMoneyInProgressByMeetingId(meetingId)
				.stream()
				.map(InProgressGameResponse::from)
				.toList();
		if (!games.isEmpty()) {
			throw new BusinessException(ErrorCode.MONEY_GAME_IN_PROGRESS, games);
		}
	}

	private void requireWarningsAcknowledged(SettlementResult result, boolean acknowledgeWarnings) {
		if (!result.warnings().isEmpty() && !acknowledgeWarnings) {
			throw new BusinessException(ErrorCode.WARNING_UNCONFIRMED,
					result.warnings().stream().map(SettlementWarningResponse::from).toList());
		}
	}
}
