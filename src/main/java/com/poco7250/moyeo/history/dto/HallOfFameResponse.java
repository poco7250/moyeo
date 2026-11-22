package com.poco7250.moyeo.history.dto;

import java.util.List;

/**
 * 그룹 명예의 전당(PRD H-2). 벌칙 순위·보드게임 꼴등 순위는 그룹 멤버 전원을 담는다.
 *
 * @param year 집계 연도. 전체 기간이면 null
 * @param penaltyRanking 벌칙 당첨 횟수 순위(횟수 → 금액 많은 순 → 그룹 가입 순)
 * @param boardGameLoserRanking 보드게임 꼴등 횟수 순위(꼴등 횟수 → 그룹 가입 순)
 * @param noShowRanking 노쇼 횟수 순위(모임 단위 횟수 → 그룹 가입 순)
 * @param attendanceLog 지난 모임별 참석자·노쇼 멤버(최근 순)
 */
public record HallOfFameResponse(Integer year, List<PenaltyRankResponse> penaltyRanking,
		List<BoardGameLoserRankResponse> boardGameLoserRanking, List<NoShowRankResponse> noShowRanking,
		List<AttendanceLogResponse> attendanceLog) {
}
