package com.poco7250.moyeo.history.service;

import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.settlement.domain.Settlement;
import java.util.List;
import java.util.Map;

/**
 * 한 페이지의 모임 히스토리를 조립하는 데 필요한 데이터를 한 번에 읽어 둔 묶음.
 *
 * @param rounds 확정 장소 후보까지 읽은 차수
 * @param attendances 참석(ATTEND) 응답
 * @param expenseTotals 모임 id → 지출 합계(지출 없는 모임은 없음)
 * @param settlements 모임 id → 정산
 * @param games 결과 공개된 게임 중 원 회차별 마지막 회차
 * @param penalties 그 게임들의 무효가 아닌 벌칙
 * @param orderResults 그 게임 중 순서 정하기 게임의 순번 결과
 * @param groupMembers 회원 id → 그룹 멤버십
 */
record MeetingHistorySource(List<Round> rounds, List<Attendance> attendances, Map<Long, Long> expenseTotals,
		Map<Long, Settlement> settlements, List<GameSession> games, List<Penalty> penalties,
		List<GameResult> orderResults, Map<Long, GroupMember> groupMembers) {
}
