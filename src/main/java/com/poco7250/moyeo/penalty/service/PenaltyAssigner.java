package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameChangeSnapshot;
import com.poco7250.moyeo.game.dto.GameRerunRevealSnapshot;
import com.poco7250.moyeo.game.dto.GameRevealSnapshot;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임 결과가 공개될 때 벌칙자를 확정한다(PRD R-5).
 * 다시 돌린 회차면 같은 트랜잭션에서 원 회차 벌칙을 무효로 돌리고, 원 회차가 면제였으면 새 벌칙도 면제로 만든다.
 * 공개 전이와 함께 커밋·롤백돼야 해서 호출자 트랜잭션 안에서만 동작한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PenaltyAssigner {

	private final PenaltyRepository penaltyRepository;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;

	/**
	 * 공개된 게임의 벌칙자를 벌칙으로 만든다. 벌칙 종류가 없음이면 아무것도 만들지 않는다.
	 * 금액이 정해진 금액형이면 APPLIED, 그 밖에는 ASSIGNED로 만들고, 다시 돌린 회차면 원 회차의 면제를 이어받는다.
	 * 금액형이면 금액 영향 변경으로 정산에 반영하고 변경 로그를 남긴다. 금액은 여기서 계산하지 않는다.
	 * 모임 게스트(G-6) 벌칙은 정산에서 빠지므로, 새 벌칙자와 무효가 된 원 벌칙자가 전부 게스트면 정산을 건드리지 않는다.
	 *
	 * @param actorId 게임을 시작한 회원
	 * @param penalizedMembers 추첨에서 벌칙자로 뽑힌 회원(순서 무관)
	 */
	public void onRevealed(GameSession game, Long actorId, List<Member> penalizedMembers) {
		if (game.getPenaltyKind() == PenaltyKind.NONE) {
			return;
		}
		RerunTakeover takeover = takeOverOriginal(game);
		// 초과분 차감 순서가 Penalty.id를 따르므로 insert 순서를 회원 id 순으로 고정한다
		List<Member> ordered = penalizedMembers.stream().sorted(Comparator.comparing(Member::getId)).toList();
		penaltyRepository.saveAll(
				ordered.stream().map(member -> takeover.condition().newPenalty(game, member)).toList());
		boolean memberAffected = takeover.voidedMemberPenalty() || ordered.stream().anyMatch(m -> !m.isGuest());
		if (game.chargesMoney() && memberAffected) {
			reflectToSettlement(game, actorId, ordered.stream().map(Member::getId).toList(), takeover);
		}
	}

	/**
	 * 다시 돌린 회차면 원 회차의 비무효 벌칙을 무효로 돌린다.
	 * 원 회차 벌칙은 지우지 않아 이력에 남는다.
	 */
	private RerunTakeover takeOverOriginal(GameSession game) {
		GameSession original = game.getRerunOf();
		if (original == null) {
			return RerunTakeover.NONE;
		}
		List<Penalty> activeOriginals = penaltyRepository
				.findAllByGameSessionIdAndStatusNot(original.getId(), PenaltyStatus.VOIDED);
		InheritedCondition condition = InheritedCondition.from(activeOriginals);
		activeOriginals.forEach(Penalty::voidOut);
		List<Long> voidedMemberIds = activeOriginals.stream()
				.map(penalty -> penalty.getMember().getId())
				.sorted()
				.toList();
		boolean voidedMemberPenalty = activeOriginals.stream().anyMatch(penalty -> !penalty.getMember().isGuest());
		return new RerunTakeover(original.getId(), condition, voidedMemberIds, voidedMemberPenalty);
	}

	private void reflectToSettlement(GameSession game, Long actorId, List<Long> penaltyMemberIds,
			RerunTakeover takeover) {
		Long meetingId = game.getMeeting().getId();
		settlementGuard.lockForAmountChange(meetingId);
		settlementGuard.markAmountChanged(meetingId, actorId,
				List.of(new SettlementChangeSummary(ChangeLogEntityType.GAME_SESSION, game.getId(), ChangeType.UPDATE)));
		changeLogRecorder.record(meetingId, actorId, ChangeLogEntityType.GAME_SESSION, game.getId(), ChangeType.UPDATE,
				GameChangeSnapshot.of(game, GameStatus.WAITING), revealSnapshot(game, penaltyMemberIds, takeover));
	}

	private ChangeLogSnapshot revealSnapshot(GameSession game, List<Long> penaltyMemberIds, RerunTakeover takeover) {
		if (!takeover.isRerun()) {
			return GameRevealSnapshot.of(game, penaltyMemberIds);
		}
		return GameRerunRevealSnapshot.of(game, penaltyMemberIds, takeover.rerunOfId(), takeover.voidedMemberIds(),
				takeover.condition().status().name());
	}
}
