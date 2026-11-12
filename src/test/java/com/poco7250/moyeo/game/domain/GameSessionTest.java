package com.poco7250.moyeo.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class GameSessionTest {

	private static final Long CREATOR_ID = 1L;
	private static final Long HOST_ID = 2L;
	private static final Long OTHER_ID = 3L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 21, 0, 0, 123_456_000);

	@Test
	@DisplayName("새 게임은 WAITING이고 공개 시각이 없다")
	void createWaiting() {
		GameSession session = sessionWith(GameStatus.WAITING);

		assertThat(session.getStatus()).isEqualTo(GameStatus.WAITING);
		assertThat(session.getCreatedBy()).isEqualTo(CREATOR_ID);
		assertThat(session.getRevealedAt()).isNull();
		assertThat(session.getApplyOrderAt()).isNull();
	}

	@Test
	@DisplayName("WAITING 게임을 시작하면 PLAYING이 된다")
	void start() {
		GameSession session = sessionWith(GameStatus.WAITING);

		session.start();

		assertThat(session.getStatus()).isEqualTo(GameStatus.PLAYING);
	}

	@ParameterizedTest
	@EnumSource(value = GameStatus.class, names = "WAITING", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("WAITING이 아니면 시작할 수 없고 상태는 그대로다")
	void startRejected(GameStatus status) {
		GameSession session = sessionWith(status);

		assertRejected(session::start, ErrorCode.GAME_INVALID_TRANSITION);
		assertThat(session.getStatus()).isEqualTo(status);
	}

	@Test
	@DisplayName("PLAYING 게임을 공개하면 REVEALED가 되고 공개 시각과 적용 순서 시각이 기록된다")
	void reveal() {
		GameSession session = sessionWith(GameStatus.PLAYING);

		session.reveal(NOW);

		assertThat(session.getStatus()).isEqualTo(GameStatus.REVEALED);
		assertThat(session.getRevealedAt()).isEqualTo(NOW);
		assertThat(session.getApplyOrderAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("적용 순서 시각이 이미 있으면 유지하고 공개 시각만 기록한다")
	void revealKeepsInheritedApplyOrderAt() {
		GameSession session = sessionWith(GameStatus.PLAYING);
		LocalDateTime inherited = NOW.minusHours(1);
		ReflectionTestUtils.setField(session, "applyOrderAt", inherited);

		session.reveal(NOW);

		assertThat(session.getRevealedAt()).isEqualTo(NOW);
		assertThat(session.getApplyOrderAt()).isEqualTo(inherited);
	}

	@Test
	@DisplayName("공개 시각의 마이크로초 아래 자리는 버린다")
	void revealTruncatesToMicros() {
		GameSession session = sessionWith(GameStatus.PLAYING);

		session.reveal(NOW.plusNanos(789));

		assertThat(session.getRevealedAt()).isEqualTo(NOW);
		assertThat(session.getApplyOrderAt()).isEqualTo(NOW);
	}

	@ParameterizedTest
	@EnumSource(value = GameStatus.class, names = "PLAYING", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("PLAYING이 아니면 공개할 수 없고 공개 시각도 남지 않는다")
	void revealRejected(GameStatus status) {
		GameSession session = sessionWith(status);

		assertRejected(() -> session.reveal(NOW), ErrorCode.GAME_INVALID_TRANSITION);
		assertThat(session.getStatus()).isEqualTo(status);
		assertThat(session.getRevealedAt()).isNull();
		assertThat(session.getApplyOrderAt()).isNull();
	}

	@Test
	@DisplayName("REVEALED 게임을 종료하면 CLOSED가 된다")
	void close() {
		GameSession session = sessionWith(GameStatus.REVEALED);

		session.close();

		assertThat(session.getStatus()).isEqualTo(GameStatus.CLOSED);
	}

	@ParameterizedTest
	@EnumSource(value = GameStatus.class, names = "REVEALED", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("REVEALED가 아니면 종료할 수 없고 상태는 그대로다")
	void closeRejected(GameStatus status) {
		GameSession session = sessionWith(status);

		assertRejected(session::close, ErrorCode.GAME_INVALID_TRANSITION);
		assertThat(session.getStatus()).isEqualTo(status);
	}

	@ParameterizedTest
	@EnumSource(value = GameStatus.class, names = {"WAITING", "PLAYING"})
	@DisplayName("WAITING·PLAYING 게임은 취소할 수 있다")
	void cancel(GameStatus status) {
		GameSession session = sessionWith(status);

		session.cancel();

		assertThat(session.getStatus()).isEqualTo(GameStatus.CANCELED);
	}

	@ParameterizedTest
	@EnumSource(value = GameStatus.class, names = {"REVEALED", "CLOSED", "CANCELED"})
	@DisplayName("공개 후·종료·취소된 게임은 취소할 수 없고 상태는 그대로다")
	void cancelRejected(GameStatus status) {
		GameSession session = sessionWith(status);

		assertRejected(session::cancel, ErrorCode.GAME_NOT_CANCELABLE);
		assertThat(session.getStatus()).isEqualTo(status);
	}

	@Test
	@DisplayName("진행자와 모임 호스트만 운영자다")
	void isOperator() {
		GameSession session = sessionWith(GameStatus.WAITING);
		Meeting meeting = Meeting.create(null, "연말 모임", NOW, null, null, HOST_ID);

		assertThat(session.isOperator(CREATOR_ID, meeting)).isTrue();
		assertThat(session.isOperator(HOST_ID, meeting)).isTrue();
		assertThat(session.isOperator(OTHER_ID, meeting)).isFalse();
		assertThat(session.isOperator(null, meeting)).isFalse();
	}

	@Test
	@DisplayName("저장된 첫 회차는 자기 id를 원 회차 id로 갖는다")
	void markAsRoot() {
		GameSession session = sessionWith(GameStatus.WAITING);
		ReflectionTestUtils.setField(session, "id", 7L);

		session.markAsRoot();

		assertThat(session.getRootSessionId()).isEqualTo(7L);
	}

	@Test
	@DisplayName("저장 전 게임은 원 회차로 표시할 수 없다")
	void markAsRootRejectedBeforeSave() {
		GameSession session = sessionWith(GameStatus.WAITING);

		assertThatThrownBy(session::markAsRoot).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("다시 돌린 회차는 WAITING으로 생기고 설정을 복제하며 적용 순서와 원 회차 id를 이어받는다")
	void rerunOfCopiesAndInherits() {
		GameSession original = GameSession.create(null, GameType.DRAW, CREATOR_ID, PenaltyKind.MONEY, "꼴찌가 2차 30%", 2);
		ReflectionTestUtils.setField(original, "id", 10L);
		original.markAsRoot();
		original.start();
		original.recordDraw(42L);
		original.reveal(NOW);

		GameSession rerun = GameSession.rerunOf(original, HOST_ID);

		assertThat(rerun.getStatus()).isEqualTo(GameStatus.WAITING);
		assertThat(rerun.getRerunOf()).isSameAs(original);
		assertThat(rerun.getCreatedBy()).isEqualTo(HOST_ID);
		assertThat(rerun.getType()).isEqualTo(GameType.DRAW);
		assertThat(rerun.getPenaltyKind()).isEqualTo(PenaltyKind.MONEY);
		assertThat(rerun.getPenaltyNote()).isEqualTo("꼴찌가 2차 30%");
		assertThat(rerun.getPenaltyCount()).isEqualTo(2);
		assertThat(rerun.getApplyOrderAt()).isEqualTo(original.getApplyOrderAt());
		assertThat(rerun.getRootSessionId()).isEqualTo(10L);
		assertThat(rerun.getSeed()).isNull();
		assertThat(rerun.getRevealedAt()).isNull();
	}

	@Test
	@DisplayName("다시 돌린 회차를 취소하면 rerunOf를 비우고 canceledRerunOf에 원 회차를 남긴다")
	void cancelRerunReleasesOriginal() {
		GameSession original = sessionWith(GameStatus.REVEALED);
		GameSession rerun = GameSession.rerunOf(original, CREATOR_ID);

		rerun.cancel();

		assertThat(rerun.getStatus()).isEqualTo(GameStatus.CANCELED);
		assertThat(rerun.getRerunOf()).isNull();
		assertThat(rerun.getCanceledRerunOf()).isSameAs(original);
	}

	@Test
	@DisplayName("처음 만든 회차를 취소하면 다시 돌리기 이력은 비어 있다")
	void cancelRootLeavesRerunFieldsEmpty() {
		GameSession session = sessionWith(GameStatus.WAITING);

		session.cancel();

		assertThat(session.getRerunOf()).isNull();
		assertThat(session.getCanceledRerunOf()).isNull();
	}

	@ParameterizedTest
	@EnumSource(GameStatus.class)
	@DisplayName("공개된(REVEALED·CLOSED) 회차만 다시 돌릴 수 있는 상태다")
	void isRerunnable(GameStatus status) {
		boolean expected = status == GameStatus.REVEALED || status == GameStatus.CLOSED;

		assertThat(sessionWith(status).isRerunnable()).isEqualTo(expected);
	}

	private void assertRejected(ThrowingCallable transition, ErrorCode expected) {
		assertThatThrownBy(transition)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(expected);
	}

	private GameSession sessionWith(GameStatus status) {
		GameSession session = GameSession.create(null, GameType.LADDER, CREATOR_ID, PenaltyKind.MONEY, null, 1);
		ReflectionTestUtils.setField(session, "status", status);
		return session;
	}
}
