package com.poco7250.moyeo.penalty.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	@DisplayName("새 벌칙은 ASSIGNED이고 면제 정보가 없다")
	void assign() {
		Penalty penalty = Penalty.assign(null, null);

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.ASSIGNED);
		assertThat(penalty.getWaivedBy()).isNull();
		assertThat(penalty.getWaivedAt()).isNull();
		assertThat(penalty.isVoided()).isFalse();
	}

	@Test
	@DisplayName("ASSIGNED 벌칙에 조건을 적용하면 APPLIED가 된다")
	void applyFromAssigned() {
		Penalty penalty = penaltyWith(PenaltyStatus.ASSIGNED);

		penalty.apply();

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.APPLIED);
	}

	@Test
	@DisplayName("WAIVED 벌칙에 조건을 적용하면 APPLIED가 되고 면제 정보가 비워진다")
	void applyFromWaived() {
		Penalty penalty = waivedPenalty();

		penalty.apply();

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.APPLIED);
		assertThat(penalty.getWaivedBy()).isNull();
		assertThat(penalty.getWaivedAt()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyStatus.class, names = {"APPLIED", "VOIDED"})
	@DisplayName("APPLIED·VOIDED 벌칙에는 조건을 적용할 수 없고 상태는 그대로다")
	void applyRejected(PenaltyStatus status) {
		Penalty penalty = penaltyWith(status);

		assertRejected(penalty::apply);
		assertThat(penalty.getStatus()).isEqualTo(status);
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyStatus.class, names = {"ASSIGNED", "APPLIED"})
	@DisplayName("ASSIGNED·APPLIED 벌칙을 면제하면 WAIVED가 되고 면제자와 시각이 기록된다")
	void waive(PenaltyStatus status) {
		Penalty penalty = penaltyWith(status);

		penalty.waive(HOST_ID, NOW);

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(penalty.getWaivedBy()).isEqualTo(HOST_ID);
		assertThat(penalty.getWaivedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("이미 WAIVED인 벌칙은 다시 면제할 수 없고 처음 면제 정보가 남는다")
	void waiveRejectedWhenWaived() {
		Penalty penalty = waivedPenalty();

		assertRejected(() -> penalty.waive(99L, NOW.plusHours(1)));
		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.WAIVED);
		assertThat(penalty.getWaivedBy()).isEqualTo(HOST_ID);
		assertThat(penalty.getWaivedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("WAIVED 벌칙의 면제를 취소하면 ASSIGNED가 되고 면제 정보가 비워진다")
	void unwaive() {
		Penalty penalty = waivedPenalty();

		penalty.unwaive();

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.ASSIGNED);
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

	@Test
	@DisplayName("APPLIED 벌칙의 조건 적용을 되돌리면 ASSIGNED가 된다")
	void unapply() {
		Penalty penalty = penaltyWith(PenaltyStatus.APPLIED);

		penalty.unapply();

		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.ASSIGNED);
	}

	@ParameterizedTest
	@EnumSource(value = PenaltyStatus.class, names = "APPLIED", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("APPLIED가 아니면 조건 적용을 되돌릴 수 없고 상태는 그대로다")
	void unapplyRejected(PenaltyStatus status) {
		Penalty penalty = penaltyWith(status);

		assertRejected(penalty::unapply);
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
		assertThat(penalty.getWaivedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("VOIDED 벌칙은 어떤 전이도 할 수 없다")
	void voidedIsTerminal() {
		Penalty penalty = penaltyWith(PenaltyStatus.VOIDED);

		assertRejected(penalty::apply);
		assertRejected(() -> penalty.waive(HOST_ID, NOW));
		assertRejected(penalty::unwaive);
		assertRejected(penalty::unapply);
		assertRejected(penalty::voidOut);
		assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.VOIDED);
		assertThat(penalty.getWaivedBy()).isNull();
	}

	private void assertRejected(ThrowingCallable transition) {
		assertThatThrownBy(transition)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PENALTY_NOT_CONFIGURABLE);
	}

	private Penalty waivedPenalty() {
		Penalty penalty = Penalty.assign(null, null);
		penalty.waive(HOST_ID, NOW);
		return penalty;
	}

	private Penalty penaltyWith(PenaltyStatus status) {
		Penalty penalty = Penalty.assign(null, null);
		ReflectionTestUtils.setField(penalty, "status", status);
		return penalty;
	}
}
