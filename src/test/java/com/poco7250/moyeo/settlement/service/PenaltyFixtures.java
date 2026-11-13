package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.settlement.domain.Expense;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 정산 서비스 단위 테스트용 벌칙·게임·지출 엔티티 픽스처. id는 리플렉션으로 넣는다.
 */
final class PenaltyFixtures {

	private PenaltyFixtures() {
	}

	/**
	 * 모임 없이 차수 번호·이름만 가진 차수.
	 */
	static Round round(Long id, int seq, String name) {
		Round round = Round.create(null, seq, name);
		ReflectionTestUtils.setField(round, "id", id);
		return round;
	}

	/**
	 * 결과가 공개된 금액형 사다리 원 회차.
	 */
	static GameSession revealedMoneyGame(Long id, Round round, LocalDateTime revealedAt) {
		GameSession game = GameSession.create(round, GameType.LADDER, 1L, PenaltyKind.MONEY, null, 1);
		ReflectionTestUtils.setField(game, "id", id);
		game.markAsRoot();
		game.start();
		game.reveal(revealedAt);
		return game;
	}

	static Member member(Long id) {
		Member member = Member.ofKakao(id + 1000, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	static Penalty penalty(Long id, GameSession game, Long memberId) {
		Penalty penalty = Penalty.assign(game, member(memberId));
		ReflectionTestUtils.setField(penalty, "id", id);
		return penalty;
	}

	static Expense expense(Long id, Round round, Long payerId, long amount, String memo) {
		Expense expense = Expense.create(round, member(payerId), amount, memo);
		ReflectionTestUtils.setField(expense, "id", id);
		return expense;
	}

	static PenaltyRule ratioRule(Long id, GameSession game, int ratioPct, Expense target, Long setBy,
			LocalDateTime setAt) {
		PenaltyRule rule = PenaltyRule.create(game, PenaltyMoneyType.RATIO, null, ratioPct, target, setBy, setAt);
		ReflectionTestUtils.setField(rule, "id", id);
		return rule;
	}
}
