package com.poco7250.moyeo.meeting.domain;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 차수별 응답을 한 번에 채우는 단축 옵션(PRD M-3). 1차를 기준으로 나눈다(2026-11-03 결정).
 * 차수가 3개 이상이면 늦참은 2차부터 끝까지 참석, 1차만은 2차부터 끝까지 불참이다.
 */
public enum AttendanceShortcut {

	// 늦참(2차부터): 1차 불참, 나머지 참석
	LATE_FROM_SECOND(AttendanceStatus.ABSENT, AttendanceStatus.ATTEND),
	// 1차만: 1차 참석, 나머지 불참
	FIRST_ONLY(AttendanceStatus.ATTEND, AttendanceStatus.ABSENT);

	private final AttendanceStatus first;
	private final AttendanceStatus rest;

	AttendanceShortcut(AttendanceStatus first, AttendanceStatus rest) {
		this.first = first;
		this.rest = rest;
	}

	/**
	 * 차수 개수만큼 1차부터 순서대로 응답 상태를 펼친다.
	 *
	 * @throws BusinessException 차수가 2개 미만이면 ATTENDANCE_SHORTCUT_NOT_APPLICABLE
	 */
	public List<AttendanceStatus> expand(int roundCount) {
		if (roundCount < 2) {
			throw new BusinessException(ErrorCode.ATTENDANCE_SHORTCUT_NOT_APPLICABLE);
		}
		return IntStream.range(0, roundCount)
				.mapToObj(i -> i == 0 ? first : rest)
				.toList();
	}
}
