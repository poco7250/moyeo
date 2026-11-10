package com.poco7250.moyeo.game.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차수에서 벌칙자를 정하는 게임 한 회차. 상태 전이는 version 낙관적 락으로 먼저 온 요청만 반영한다.
 * seed와 resultData는 공개 전 결과이므로 응답 DTO에 매핑하지 않는다.
 */
@Getter
@Entity
@Table(
		name = "game_session",
		// MySQL 유니크는 NULL을 여러 건 허용하므로 다시 돌린 회차끼리만 겹치지 않는다
		uniqueConstraints = @UniqueConstraint(
				name = "uk_game_session_rerun_of_id",
				columnNames = "rerun_of_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false)
	private Round round;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GameType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GameStatus status;

	// 진행자 = 게임을 만든 회원(다시 돌린 회차는 요청자)
	@Column(nullable = false, updatable = false)
	private Long createdBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PenaltyKind penaltyKind;

	// 공개 전부터 보이는 안내 문구. 정산 계산에는 쓰지 않는다
	@Column(length = 100)
	private String penaltyNote;

	private Long seed;

	// 게임 종류마다 구조가 다른 결과 배열(JSON)
	@Column(columnDefinition = "TEXT")
	private String resultData;

	private LocalDateTime revealedAt;

	// 벌칙 적용 순서 기준. 다시 돌린 회차는 원 회차 값을 이어받는다
	@Column(columnDefinition = "DATETIME(6)")
	private LocalDateTime applyOrderAt;

	// 적용 순서 타이브레이크용 원 회차 id. id는 insert 후에야 생기므로 컬럼은 nullable이다
	private Long rootSessionId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rerun_of_id")
	private GameSession rerunOf;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "canceled_rerun_of_id")
	private GameSession canceledRerunOf;

	private Integer timeLimitSeconds;

	@Version
	private Long version;

	private GameSession(Round round, GameType type, Long createdBy, PenaltyKind penaltyKind,
			String penaltyNote, Integer timeLimitSeconds) {
		this.round = round;
		this.type = type;
		this.status = GameStatus.WAITING;
		this.createdBy = createdBy;
		this.penaltyKind = penaltyKind;
		this.penaltyNote = penaltyNote;
		this.timeLimitSeconds = timeLimitSeconds;
	}

	/**
	 * 대기(WAITING) 상태의 새 게임을 만든다. 만든 회원이 진행자가 된다.
	 */
	public static GameSession create(Round round, GameType type, Long createdBy, PenaltyKind penaltyKind,
			String penaltyNote, Integer timeLimitSeconds) {
		return new GameSession(round, type, createdBy, penaltyKind, penaltyNote, timeLimitSeconds);
	}

	/**
	 * 저장으로 id가 생긴 첫 회차를 자기 자신의 원 회차로 표시한다.
	 *
	 * @throws IllegalStateException 아직 저장되지 않아 id가 없으면
	 */
	public void markAsRoot() {
		if (id == null) {
			throw new IllegalStateException("저장된 게임만 원 회차로 표시할 수 있습니다.");
		}
		this.rootSessionId = id;
	}

	/**
	 * 게임을 시작한다(WAITING → PLAYING).
	 *
	 * @throws BusinessException WAITING이 아니면 GAME_INVALID_TRANSITION
	 */
	public void start() {
		requireStatus(GameStatus.WAITING);
		this.status = GameStatus.PLAYING;
	}

	/**
	 * 결과를 공개한다(PLAYING → REVEALED). 적용 순서 시각은 비어 있을 때만 기록해 다시 돌린 회차의 상속값을 지킨다.
	 *
	 * @throws BusinessException PLAYING이 아니면 GAME_INVALID_TRANSITION
	 */
	public void reveal(LocalDateTime now) {
		requireStatus(GameStatus.PLAYING);
		this.status = GameStatus.REVEALED;
		// DATETIME(6)에 저장되는 값과 메모리 값을 같게 맞춘다
		LocalDateTime revealedTime = now.truncatedTo(ChronoUnit.MICROS);
		this.revealedAt = revealedTime;
		if (applyOrderAt == null) {
			this.applyOrderAt = revealedTime;
		}
	}

	/**
	 * 공개된 게임을 종료한다(REVEALED → CLOSED).
	 *
	 * @throws BusinessException REVEALED가 아니면 GAME_INVALID_TRANSITION
	 */
	public void close() {
		requireStatus(GameStatus.REVEALED);
		this.status = GameStatus.CLOSED;
	}

	/**
	 * 공개 전 게임을 취소한다(WAITING·PLAYING → CANCELED).
	 *
	 * @throws BusinessException WAITING·PLAYING이 아니면 GAME_NOT_CANCELABLE
	 */
	public void cancel() {
		if (status != GameStatus.WAITING && status != GameStatus.PLAYING) {
			throw new BusinessException(ErrorCode.GAME_NOT_CANCELABLE);
		}
		this.status = GameStatus.CANCELED;
	}

	/**
	 * 주어진 회원이 이 게임의 진행자이거나 모임 호스트인지 확인한다.
	 */
	public boolean isOperator(Long memberId, Meeting meeting) {
		return memberId != null && (Objects.equals(createdBy, memberId) || meeting.isHost(memberId));
	}

	private void requireStatus(GameStatus required) {
		if (status != required) {
			throw new BusinessException(ErrorCode.GAME_INVALID_TRANSITION);
		}
	}
}
