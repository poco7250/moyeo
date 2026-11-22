package com.poco7250.moyeo.history.service;

import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.penalty.domain.Penalty;
import java.util.List;
import java.util.Map;

/**
 * 연말 결산을 조립하는 데 필요한 데이터를 한 번에 읽어 둔 묶음.
 *
 * @param groupName 공유 문구에 쓸 그룹 이름
 * @param meetings 그해 지난 모임(최근 순)
 * @param rounds 그 모임들의 차수(확정 장소 포함)
 * @param attendances 그 차수들의 참석(ATTEND) 응답
 * @param noShows 그 차수들의 노쇼 응답
 * @param totalExpense 그 모임들의 현재 지출 합계(원)
 * @param confirmedMeetingCount 정산이 한 번이라도 확정된 모임 수
 * @param penalties 그 차수들 게임의 무효가 아닌 벌칙
 * @param penaltyAmounts 회원 id → 마지막 확정 리비전 기준 벌칙 부담액 합
 * @param shareAmounts 회원 id → 마지막 확정 리비전 기준 정산 부담액 합
 * @param groupMembers 그룹 멤버 전원(가입 순)
 */
record YearReviewSource(String groupName, List<Meeting> meetings, List<Round> rounds, List<Attendance> attendances,
		List<Attendance> noShows, long totalExpense, long confirmedMeetingCount, List<Penalty> penalties,
		Map<Long, Long> penaltyAmounts, Map<Long, Long> shareAmounts, List<GroupMember> groupMembers) {

	/**
	 * 그해 모임이 없을 때의 묶음(멤버만 있음).
	 */
	static YearReviewSource empty(String groupName, List<GroupMember> groupMembers) {
		return new YearReviewSource(groupName, List.of(), List.of(), List.of(), List.of(), 0, 0, List.of(), Map.of(),
				Map.of(), groupMembers);
	}

	/**
	 * 벌칙·노쇼 순위 계산을 명예의 전당과 공유하려고 그쪽 묶음으로 바꾼다(보드게임은 쓰지 않음).
	 */
	HallOfFameSource toHallOfFameSource() {
		return new HallOfFameSource(meetings, rounds, attendances, noShows, penalties, penaltyAmounts, List.of(),
				groupMembers);
	}
}
