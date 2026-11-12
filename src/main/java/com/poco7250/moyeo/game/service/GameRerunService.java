package com.poco7250.moyeo.game.service;

import com.poco7250.moyeo.game.domain.GameParticipant;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameRerunSnapshot;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameParticipantRepository;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.auth.GameAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다시 돌리기 요청(PRD R-5). 요청 시점에는 새 회차만 만들고 원 회차의 벌칙·조건은 건드리지 않는다.
 * 원 회차 벌칙 무효 처리와 조건 상속은 새 회차 결과가 공개될 때 일어난다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GameRerunService {

	private final GameAccessChecker gameAccessChecker;
	private final GameSessionRepository gameSessionRepository;
	private final GameParticipantRepository gameParticipantRepository;
	private final GameStateAssembler gameStateAssembler;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 공개된 회차를 다시 돌릴 새 회차(WAITING)를 만든다. 설정과 참가자는 원 회차를 복제하고 요청자가 진행자가 된다.
	 *
	 * @throws BusinessException 원 회차 진행자·호스트가 아니면 NOT_GAME_OPERATOR, 공개 전·취소된 회차이거나
	 *         이미 후속 회차가 있으면 GAME_NOT_RERUNNABLE, 동시 요청에 밀리면 RERUN_ALREADY_EXISTS
	 */
	public GameStateResponse request(Long originalId, Long requesterId) {
		GameSession original = gameAccessChecker.requireOperator(originalId, requesterId);
		requireRerunnable(original);
		GameSession rerun = saveRerun(original, requesterId);
		copyParticipants(original, rerun);
		if (rerun.getPenaltyKind() == PenaltyKind.MONEY) {
			reflectToSettlement(rerun, originalId, requesterId);
		}
		GameStateResponse state = gameStateAssembler.assemble(rerun);
		eventPublisher.publishEvent(GameStateChangedEvent.toMeeting(rerun.getRound().getMeeting().getId(), state));
		return state;
	}

	private void requireRerunnable(GameSession original) {
		if (!original.isRerunnable() || gameSessionRepository.existsByRerunOfId(original.getId())) {
			throw new BusinessException(ErrorCode.GAME_NOT_RERUNNABLE);
		}
	}

	/**
	 * 새 회차를 바로 insert해 rerunOf 유니크 제약으로 동시 요청 중 하나만 통과시킨다.
	 */
	private GameSession saveRerun(GameSession original, Long requesterId) {
		try {
			return gameSessionRepository.saveAndFlush(GameSession.rerunOf(original, requesterId));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.RERUN_ALREADY_EXISTS);
		}
	}

	private void copyParticipants(GameSession original, GameSession rerun) {
		List<GameParticipant> participants = gameParticipantRepository
				.findAllByGameSessionIdOrderByIdAsc(original.getId()).stream()
				.map(participant -> GameParticipant.create(rerun, participant.getMember()))
				.toList();
		gameParticipantRepository.saveAll(participants);
	}

	private void reflectToSettlement(GameSession rerun, Long originalId, Long requesterId) {
		Long meetingId = rerun.getRound().getMeeting().getId();
		settlementGuard.lockForAmountChange(meetingId);
		settlementGuard.markAmountChanged(meetingId, requesterId, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.GAME_SESSION, rerun.getId(), ChangeType.CREATE)));
		changeLogRecorder.record(meetingId, requesterId, ChangeLogEntityType.GAME_SESSION, rerun.getId(),
				ChangeType.CREATE, null, GameRerunSnapshot.of(rerun, originalId));
	}
}
