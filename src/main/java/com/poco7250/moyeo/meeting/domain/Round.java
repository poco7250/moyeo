package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모임의 차수(1차 밥, 2차 볼링 …). 장소는 모임이 아니라 차수마다 따로 정한다.
 * 투표 자동 마감과 호스트 수동 확정이 겹치면 version 낙관적 락으로 먼저 온 쪽만 반영한다.
 * 확정 후보(confirmedCandidate)는 PlaceCandidate가 생기는 Task 014에서 추가한다.
 */
@Getter
@Entity
// SQL ROUND 함수와 헷갈리지 않도록 테이블명을 따로 둔다
@Table(
		name = "meeting_round",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_meeting_round_meeting_id_seq",
				columnNames = {"meeting_id", "seq"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Round extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meeting_id", nullable = false)
	private Meeting meeting;

	// 1부터 시작하는 차수 순서
	@Column(nullable = false)
	private int seq;

	// 차수 이름(예: "밥", "볼링")
	@Column(nullable = false, length = 30)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlaceStatus placeStatus;

	private LocalDateTime placeVoteDeadline;

	@Version
	private Long version;

	private Round(Meeting meeting, int seq, String name) {
		this.meeting = meeting;
		this.seq = seq;
		this.name = name;
		this.placeStatus = PlaceStatus.UNDECIDED;
	}

	/**
	 * 모임에 차수를 만든다. 장소 상태는 미정(UNDECIDED)으로 시작한다.
	 */
	public static Round create(Meeting meeting, int seq, String name) {
		return new Round(meeting, seq, name);
	}
}
