package com.poco7250.moyeo.penalty.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameChangeSnapshot;
import com.poco7250.moyeo.game.dto.GameRevealSnapshot;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
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
	 * 공개된 게임의 벌칙자를 ASSIGNED 벌칙으로 만든다. 벌칙 종류가 없음이면 아무것도 만들지 않는다.
	 * 금액형이면 금액 영향 변경으로 정산에 반영하고 변경 로그를 남긴다. 금액은 여기서 계산하지 않는다.
	 *
	 * @param actorId 게임을 시작한 회원
	 * @param penalizedMembers 추첨에서 벌칙자로 뽑힌 회원(순서 무관)
	 */
	public void onRevealed(GameSession game, Long actorId, List<Member> penalizedMembers) {
		if (game.getPenaltyKind() == PenaltyKind.NONE) {
			return;
		}
		// 초과분 차감 순서가 Penalty.id를 따르므로 insert 순서를 회원 id 순으로 고정한다
		List<Member> ordered = penalizedMembers.stream().sorted(Comparator.comparing(Member::getId)).toList();
		penaltyRepository.saveAll(ordered.stream().map(member -> Penalty.assign(game, member)).toList());
		if (game.getPenaltyKind() == PenaltyKind.MONEY) {
			reflectToSettlement(game, actorId, ordered.stream().map(Member::getId).toList());
		}
	}

	private void reflectToSettlement(GameSession game, Long actorId, List<Long> penaltyMemberIds) {
		Long meetingId = game.getRound().getMeeting().getId();
		settlementGuard.lockForAmountChange(meetingId);
		settlementGuard.markAmountChanged(meetingId, actorId, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.GAME_SESSION, game.getId(), ChangeType.UPDATE)));
		changeLogRecorder.record(meetingId, actorId, ChangeLogEntityType.GAME_SESSION, game.getId(), ChangeType.UPDATE,
				GameChangeSnapshot.of(game, GameStatus.WAITING), GameRevealSnapshot.of(game, penaltyMemberIds));
	}
}
