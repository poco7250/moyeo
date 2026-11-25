package com.poco7250.moyeo.penalty.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameType;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class PenaltyTest {

	private static final Long HOST_ID = 2L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 22, 0);

	@Test
	@DisplayName("금액이 정해진 금액형 게임 벌칙은 바로 APPLIED이고 면제 정보가 없다")
	void assignMoney() {
		Penalty penalty = Penalty.assign(game(PenaltyKind.MONEY, 10_000L), null);

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.APPLIED);
		assertThat(penalty.getWaivedBy()).isNull();
		assertThat(penalty.isVoided()).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyKind.class, names = {"ACTION", "NONE"})
	@DisplayName("행동형·벌칙 없음 게임 벌칙은 정산 금액이 없는 ASSIGNED다")
	void assignWithoutMoney(PenaltyKind kind) {
		assertThat(Penalty.assign(game(kind, null), null).getStatus()).isEqualTo(PenaltyStatus.ASSIGNED);
	}

	@Test
	@DisplayName("금액 없는 옛 금액형 게임 벌칙도 ASSIGNED다")
	void assignLegacyMoneyWithoutAmount() {
		GameSession legacy = game(PenaltyKind.MONEY, 10_000L);
		ReflectionTestUtils.setField(legacy, "penaltyAmount", null);

		assertThat(Penalty.assign(legacy, null).getStatus()).isEqualTo(PenaltyStatus.ASSIGNED);
	}

	@Test
	@DisplayName("APPLIED 벌칙을 면제하면 WAIVED가 되고 면제자와 시각이 기록된다")
	void waive() {
		Penalty penalty = moneyPenalty();

		penalty.waive(HOST_ID, NOW);

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(penalty.getWaivedBy()).isEqualTo(HOST_ID);
		assertThat(penalty.getWaivedAt()).isEqualTo(NOW);
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyStatus.class, names = "APPLIED", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("APPLIED가 아니면 면제할 수 없고 상태는 그대로다")
	void waiveRejected(PenaltyStatus status) {
		Penalty penalty = penaltyWith(status);

		assertRejected(() -> penalty.waive(99L, NOW));
		assertThat(penalty.getStatus()).isEqualTo(status);
	}

	@Test
	@DisplayName("이미 WAIVED인 벌칙은 다시 면제할 수 없고 처음 면제 정보가 남는다")
	void waiveRejectedWhenWaived() {
		Penalty penalty = waivedPenalty();

		assertRejected(() -> penalty.waive(99L, NOW.plusHours(1)));
		assertThat(penalty.getWaivedBy()).isEqualTo(HOST_ID);
		assertThat(penalty.getWaivedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("WAIVED 벌칙의 면제를 취소하면 다시 APPLIED가 되고 면제 정보가 비워진다")
	void unwaive() {
		Penalty penalty = waivedPenalty();

		penalty.unwaive();

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.APPLIED);
		assertThat(penalty.getWaivedBy()).isNull();
		assertThat(penalty.getWaivedAt()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyStatus.class, names = "WAIVED", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("WAIVED가 아니면 면제를 취소할 수 없고 상태는 그대로다")
	void unwaiveRejected(PenaltyStatus status) {
		Penalty penalty = penaltyWith(status);

		assertRejected(penalty::unwaive);
		assertThat(penalty.getStatus()).isEqualTo(status);
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyStatus.class, names = "VOIDED", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("무효가 아닌 벌칙은 모두 VOIDED로 바꿀 수 있다")
	void voidOut(PenaltyStatus status) {
		Penalty penalty = penaltyWith(status);

		penalty.voidOut();

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.VOIDED);
		assertThat(penalty.isVoided()).isTrue();
	}

	@Test
	@DisplayName("면제된 벌칙이 무효가 되어도 면제 정보는 이력으로 남는다")
	void voidOutKeepsWaiver() {
		Penalty penalty = waivedPenalty();

		penalty.voidOut();

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.VOIDED);
		assertThat(penalty.getWaivedBy()).isEqualTo(HOST_ID);
	}

	@Test
	@DisplayName("VOIDED 벌칙은 어떤 전이도 할 수 없다")
	void voidedIsTerminal() {
		Penalty penalty = penaltyWith(PenaltyStatus.VOIDED);

		assertRejected(() -> penalty.waive(HOST_ID, NOW));
		assertRejected(penalty::unwaive);
		assertRejected(penalty::voidOut);
		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.VOIDED);
	}

	private void assertRejected(ThrowingCallable transition) {
		assertThatThrownBy(transition)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PENALTY_NOT_CONFIGURABLE);
	}

	private static GameSession game(PenaltyKind kind, Long amount) {
		return GameSession.create(null, GameType.LADDER, 1L, kind, amount, null, 1);
	}

	private Penalty moneyPenalty() {
		return Penalty.assign(game(PenaltyKind.MONEY, 10_000L), null);
	}

	private Penalty waivedPenalty() {
		Penalty penalty = moneyPenalty();
		penalty.waive(HOST_ID, NOW);
		return penalty;
	}

	private Penalty penaltyWith(PenaltyStatus status) {
		Penalty penalty = moneyPenalty();
		ReflectionTestUtils.setField(penalty, "status", status);
		return penalty;
	}
}
