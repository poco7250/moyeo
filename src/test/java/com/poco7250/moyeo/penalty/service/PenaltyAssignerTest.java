package com.poco7250.moyeo.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameChangeSnapshot;
import com.poco7250.moyeo.game.dto.GameRevealSnapshot;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.dto.SettlementChangeSummary;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PenaltyAssignerTest {

	private static final Long GAME_ID = 300L;
	private static final Long MEETING_ID = 50L;
	private static final Long ACTOR_ID = 1L;
	private static final Long MINSU_ID = 2L;
	private static final Long YEONGHEE_ID = 3L;
	private static final LocalDateTime REVEALED_AT = LocalDateTime.of(2026, 12, 20, 21, 0);

	@Mock
	PenaltyRepository penaltyRepository;

	@Mock
	SettlementGuard settlementGuard;

	@Mock
	ChangeLogRecorder changeLogRecorder;

	@InjectMocks
	PenaltyAssigner penaltyAssigner;

	@Captor
	ArgumentCaptor<List<Penalty>> penaltiesCaptor;

	Round round;

	@BeforeEach
	void setUp() {
		round = mock(Round.class);
	}

	@Test
	@DisplayName("금액형 게임의 벌칙자 2명은 ASSIGNED로 생성되고 정산 반영과 변경 로그 1건이 남는다")
	void moneyGameAssignsAndReflectsToSettlement() {
		GameSession game = revealedGame(PenaltyKind.MONEY);
		givenMeeting();

		penaltyAssigner.onRevealed(game, ACTOR_ID, List.of(member(MINSU_ID), member(YEONGHEE_ID)));

		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).hasSize(2).allSatisfy(penalty -> {
			assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.ASSIGNED);
			assertThat(penalty.getGameSession()).isSameAs(game);
			assertThat(penalty.getWaivedBy()).isNull();
		});
		assertThat(game.getRevealedAt()).isEqualTo(REVEALED_AT);
		assertThat(game.getApplyOrderAt()).isEqualTo(REVEALED_AT);
		// 변경 로그는 변경 후 정산 상태를 담으므로 REOPENED 전이 뒤에 남아야 한다
		InOrder order = inOrder(settlementGuard, changeLogRecorder);
		order.verify(settlementGuard).lockForAmountChange(MEETING_ID);
		order.verify(settlementGuard).markAmountChanged(MEETING_ID, ACTOR_ID, List.of(
				new SettlementChangeSummary(ChangeLogEntityType.GAME_SESSION, GAME_ID, ChangeType.UPDATE)));
		order.verify(changeLogRecorder).record(eq(MEETING_ID), eq(ACTOR_ID), eq(ChangeLogEntityType.GAME_SESSION),
				eq(GAME_ID), eq(ChangeType.UPDATE),
				eq(new GameChangeSnapshot(GameStatus.WAITING, GameType.LADDER, PenaltyKind.MONEY)),
				eq(new GameRevealSnapshot(GameStatus.REVEALED, GameType.LADDER, PenaltyKind.MONEY,
						List.of(MINSU_ID, YEONGHEE_ID))));
	}

	@Test
	@DisplayName("벌칙 종류가 없음이면 벌칙을 만들지 않고 정산도 건드리지 않는다")
	void noneKindCreatesNothing() {
		penaltyAssigner.onRevealed(revealedGame(PenaltyKind.NONE), ACTOR_ID, List.of(member(MINSU_ID)));

		verifyNoInteractions(penaltyRepository, settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("행동형은 ASSIGNED 벌칙만 만들고 정산 상태와 변경 로그는 건드리지 않는다")
	void actionKindAssignsWithoutSettlement() {
		penaltyAssigner.onRevealed(revealedGame(PenaltyKind.ACTION), ACTOR_ID, List.of(member(MINSU_ID)));

		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).extracting(Penalty::getStatus).containsExactly(PenaltyStatus.ASSIGNED);
		verifyNoInteractions(settlementGuard, changeLogRecorder);
	}

	@Test
	@DisplayName("벌칙자 입력 순서가 섞여 있어도 회원 id 오름차순으로 저장한다")
	void savesInMemberIdOrder() {
		GameSession game = revealedGame(PenaltyKind.ACTION);

		penaltyAssigner.onRevealed(game, ACTOR_ID, List.of(member(9L), member(MINSU_ID), member(5L)));

		verify(penaltyRepository).saveAll(penaltiesCaptor.capture());
		assertThat(penaltiesCaptor.getValue()).extracting(penalty -> penalty.getMember().getId())
				.containsExactly(MINSU_ID, 5L, 9L);
	}

	private void givenMeeting() {
		Meeting meeting = mock(Meeting.class);
		given(meeting.getId()).willReturn(MEETING_ID);
		given(round.getMeeting()).willReturn(meeting);
	}

	private GameSession revealedGame(PenaltyKind penaltyKind) {
		GameSession game = GameSession.create(round, GameType.LADDER, ACTOR_ID, penaltyKind, null, 2);
		ReflectionTestUtils.setField(game, "id", GAME_ID);
		game.start();
		game.reveal(REVEALED_AT);
		return game;
	}

	private Member member(Long id) {
		Member member = Member.ofKakao(id + 1000, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
