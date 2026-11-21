package com.poco7250.moyeo.push.domain;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 웹 푸시 종류(Task 056 결정). 회원은 종류마다 받기를 끌 수 있다(기본 전부 켬).
 */
@Getter
@RequiredArgsConstructor
public enum PushType {

	/** 미응답자 리마인드(M-6) — 마감 전에 봐야 의미가 있어 짧게 보관 */
	ATTENDANCE_REMINDER(Duration.ofHours(6)),
	/** 그룹에 새 모임이 생김 */
	MEETING_CREATED(Duration.ofHours(24)),
	/** 차수 장소 확정(바로 확정·수동 확정·자동 마감·동점 룰렛) */
	PLACE_CONFIRMED(Duration.ofHours(24)),
	/** 날짜 투표 확정(M-5) */
	DATE_CONFIRMED(Duration.ofHours(24));

	// 기기가 꺼져 있을 때 푸시 서비스가 보관할 시간(TTL 헤더)
	private final Duration ttl;
}
