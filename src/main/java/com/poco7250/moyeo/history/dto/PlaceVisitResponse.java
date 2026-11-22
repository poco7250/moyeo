package com.poco7250.moyeo.history.dto;

import java.time.LocalDateTime;

/**
 * 연말 결산의 많이 간 장소 한 줄. 장소는 사용자가 입력한 label(앞뒤 공백 제거)로 묶는다.
 *
 * @param rank 방문 횟수 기준 순위(동점이면 같은 순위)
 * @param label 가장 최근 방문의 장소 이름
 * @param visitCount 그 장소로 확정된 차수 수
 * @param lastVisitedAt 가장 최근 방문 모임의 시작 시각
 */
public record PlaceVisitResponse(int rank, String label, long visitCount, LocalDateTime lastVisitedAt) {
}
