package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.settlement.calc.SettlementResult;
import com.poco7250.moyeo.settlement.dto.InProgressGameResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import com.poco7250.moyeo.settlement.dto.UnsetPenaltyGameResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정산 확정·재확정 전 검사(PRD S-10). 순서대로 ① 조건 미설정 금액형 벌칙 ② 대기·진행 중 금액형 게임 ③ 미확인 경고를 보고,
 * 처음 걸린 검사에서 막힌 대상 목록을 에러 상세에 실어 409로 끝낸다. 정산을 잠근 트랜잭션 안에서 불러야 검사 결과가 유지된다.
 */
@Component
@RequiredArgsConstructor
public class SettlementConfirmGate {

	private final GameSessionRepository gameSessionRepository;

	/**
	 * 확정할 수 있는지 검사한다.
	 *
	 * @param penalties 모임의 금액형·비무효 벌칙(행동형은 들어 있지 않아 차단 대상이 아님)
	 * @param result    같은 벌칙으로 계산한 정산 결과
	 * @throws BusinessException PENALTY_CONDITION_UNSET, MONEY_GAME_IN_PROGRESS, WARNING_UNCONFIRMED
	 */
	public void check(Long meetingId, MeetingPenalties penalties, SettlementResult result,
			boolean acknowledgeWarnings) {
		requireNoUnsetPenalty(penalties);
		requireNoMoneyGameInProgress(meetingId);
		requireWarningsAcknowledged(result, acknowledgeWarnings);
	}

	private void requireNoUnsetPenalty(MeetingPenalties penalties) {
		// 조회 순서(게임 순번 → 원 회차 → 벌칙 id)를 유지한 채 게임별로 묶는다
		Map<Long, List<Penalty>> unsetByGameId = penalties.penalties().stream()
				.filter(penalty -> penalty.getStatus() == PenaltyStatus.ASSIGNED)
				.collect(Collectors.groupingBy(penalty -> penalty.getGameSession().getId(), LinkedHashMap::new,
						Collectors.toList()));
		if (!unsetByGameId.isEmpty()) {
			throw new BusinessException(ErrorCode.PENALTY_CONDITION_UNSET,
					unsetByGameId.values().stream().map(UnsetPenaltyGameResponse::of).toList());
		}
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
