package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.penalty.domain.PenaltyRule;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.dto.PenaltyRuleSnapshot;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.penalty.repository.PenaltyRuleRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetExpenseRuleDetacherTest {

	private static final Long EXPENSE_ID = 31L;
	private static final LocalDateTime SET_AT = LocalDateTime.of(2026, 12, 20, 21, 30);

	@Mock
	PenaltyRuleRepository penaltyRuleRepository;

	@Mock
	PenaltyRepository penaltyRepository;

	@InjectMocks
	TargetExpenseRuleDetacher detacher;

	@Test
	@DisplayName("대상 지출의 조건을 지우고 그 게임의 APPLIED 벌칙을 모두 ASSIGNED로 되돌리며 삭제 전 값을 돌려준다")
	void detach() {
		Round round = PenaltyFixtures.round(5L, 2, "볼링장");
		Expense bowling = PenaltyFixtures.expense(EXPENSE_ID, round, 1L, 87_650L, "볼링");
		GameSession game = PenaltyFixtures.revealedMoneyGame(70L, round, LocalDateTime.of(2026, 12, 20, 21, 0));
		Penalty minsoo = PenaltyFixtures.penalty(80L, game, 2L);
		Penalty yeonghee = PenaltyFixtures.penalty(81L, game, 3L);
		minsoo.apply();
		yeonghee.apply();
		PenaltyRule rule = PenaltyFixtures.ratioRule(90L, game, 30, bowling, 1L, SET_AT);
		given(penaltyRuleRepository.findAllByTargetExpenseId(EXPENSE_ID)).willReturn(List.of(rule));
		given(penaltyRepository.findAllByGameSessionIdAndStatusNot(70L, PenaltyStatus.VOIDED))
				.willReturn(List.of(minsoo, yeonghee));

		List<DetachedRule> detached = detacher.detach(EXPENSE_ID);

		assertThat(detached).containsExactly(new DetachedRule(90L, 70L,
				new PenaltyRuleSnapshot(70L, PenaltyMoneyType.RATIO, null, 30, EXPENSE_ID, 1L, SET_AT)));
		assertThat(List.of(minsoo, yeonghee)).extracting(Penalty::getStatus)
				.containsOnly(PenaltyStatus.ASSIGNED);
		InOrder order = inOrder(penaltyRepository, penaltyRuleRepository);
		order.verify(penaltyRepository).findAllByGameSessionIdAndStatusNot(70L, PenaltyStatus.VOIDED);
		order.verify(penaltyRuleRepository).delete(rule);
	}

	@Test
	@DisplayName("대상으로 한 조건이 없으면 아무것도 바꾸지 않는다")
	void noRule() {
		given(penaltyRuleRepository.findAllByTargetExpenseId(EXPENSE_ID)).willReturn(List.of());

		assertThat(detacher.detach(EXPENSE_ID)).isEmpty();
		verifyNoInteractions(penaltyRepository);
	}
}
