package com.poco7250.moyeo.history.service;

import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.penalty.domain.Penalty;
import java.util.List;
import java.util.Map;

/**
 * 명예의 전당을 조립하는 데 필요한 데이터를 한 번에 읽어 둔 묶음.
 *
 * @param meetings 집계 대상 지난 모임(최근 순)
 * @param rounds 그 모임들의 차수
 * @param attendances 그 차수들의 참석(ATTEND) 응답
 * @param penalties 그 차수들 게임의 무효가 아닌 벌칙
 * @param penaltyAmounts 회원 id → 마지막 확정 리비전 기준 벌칙 부담액 합(없는 회원은 0)
 * @param boardGames 그 차수들의 보드게임 기록(순위 포함)
 * @param groupMembers 그룹 멤버 전원(가입 순)
 */
record HallOfFameSource(List<Meeting> meetings, List<Round> rounds, List<Attendance> attendances,
		List<Penalty> penalties, Map<Long, Long> penaltyAmounts, List<BoardGameRecord> boardGames,
		List<GroupMember> groupMembers) {

	/**
	 * 집계 대상 모임이 없을 때의 묶음(멤버만 있음).
	 */
	static HallOfFameSource empty(List<GroupMember> groupMembers) {
		return new HallOfFameSource(List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), groupMembers);
	}
}
