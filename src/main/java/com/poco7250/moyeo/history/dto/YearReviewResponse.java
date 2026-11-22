package com.poco7250.moyeo.history.dto;

import java.util.List;

/**
 * 그룹 연말 결산(PRD H-4). 대상은 그해(서울 시간)에 시작한 지난 모임이다.
 *
 * @param meetingCount 모임 수
 * @param roundCount 그 모임들의 차수 수
 * @param totalExpense 현재 지출 합계(원, 미확정 모임 포함)
 * @param confirmedMeetingCount 정산이 한 번이라도 확정된 모임 수(부담액 순위의 기준)
 * @param attendanceRanking 그룹 멤버 전원의 참석 순위(횟수 → 가입 순)
 * @param shareRanking 그룹 멤버 전원의 부담액 순위(금액 → 가입 순)
 * @param topPlaces 많이 간 장소 상위 3개(횟수 → 최근 방문 순)
 * @param penaltyKings 벌칙 순위 1위 전원(당첨 0회면 빈 목록)
 * @param noShowKings 노쇼 순위 1위 전원(노쇼 0회면 빈 목록)
 * @param shareText 단톡방에 붙여 넣을 결산 문구. 모임이 없으면 null
 */
public record YearReviewResponse(int year, long meetingCount, long roundCount, long totalExpense,
		long confirmedMeetingCount, List<AttendanceRankResponse> attendanceRanking,
		List<ShareRankResponse> shareRanking, List<PlaceVisitResponse> topPlaces,
		List<PenaltyRankResponse> penaltyKings, List<NoShowRankResponse> noShowKings, String shareText) {
}
