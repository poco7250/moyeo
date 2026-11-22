package com.poco7250.moyeo.history.dto;

import java.util.List;

/**
 * 모임 결산 카드 데이터(PRD H-3). 이미지는 프론트가 그린다.
 *
 * @param groupName 그룹 이름
 * @param meeting 모임 히스토리와 같은 요약(제목·날짜·차수별 장소·참석자·게임 벌칙자·보드게임 꼴등·총 지출·정산 상태)
 * @param shares 마지막 확정 리비전의 1인당 부담액(금액 많은 순 → 가입 순). 한 번도 확정 안 됐으면 빈 목록
 */
public record MeetingCardResponse(String groupName, MeetingHistoryResponse meeting, List<CardShareResponse> shares) {
}
