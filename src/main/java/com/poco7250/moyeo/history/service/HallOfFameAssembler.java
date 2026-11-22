package com.poco7250.moyeo.history.service;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import com.poco7250.moyeo.boardgame.domain.BoardGameRank;
import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.history.dto.AttendanceLogResponse;
import com.poco7250.moyeo.history.dto.BoardGameLoserRankResponse;
import com.poco7250.moyeo.history.dto.HallOfFameResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.NoShowRankResponse;
import com.poco7250.moyeo.history.dto.PenaltyRankResponse;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.Round;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * 읽어 둔 데이터로 명예의 전당 응답을 조립한다(조회 없음). 순위는 멤버 전원을 담고 동점이면 같은 순위를 준다.
 */
final class HallOfFameAssembler {

	private final HallOfFameSource source;
	// 회원 id → 그룹 가입 순번(정렬 기준)
	private final Map<Long, Integer> joinOrder = new HashMap<>();
	private final Map<Long, GroupMember> groupMembers = new HashMap<>();

	HallOfFameAssembler(HallOfFameSource source) {
		this.source = source;
		List<GroupMember> members = source.groupMembers();
		for (int i = 0; i < members.size(); i++) {
			Long memberId = members.get(i).getMember().getId();
			joinOrder.put(memberId, i);
			groupMembers.put(memberId, members.get(i));
		}
	}

	/**
	 * 응답을 조립한다.
	 *
	 * @param year 집계 연도(전체 기간이면 null), 응답에 그대로 싣는다
	 */
	HallOfFameResponse assemble(Integer year) {
		return new HallOfFameResponse(year, penaltyRanking(), boardGameLoserRanking(), noShowRanking(), attendanceLog());
	}

	/**
	 * 그룹 멤버 전원의 벌칙 순위를 구한다(연말 결산의 벌칙왕도 같은 계산을 쓴다).
	 */
	List<PenaltyRankResponse> penaltyRanking() {
		Map<Long, Long> counts = source.penalties().stream()
				.collect(groupingBy(p -> p.getMember().getId(), counting()));
		ToLongFunction<Long> count = id -> counts.getOrDefault(id, 0L);
		ToLongFunction<Long> amount = id -> source.penaltyAmounts().getOrDefault(id, 0L);
		List<Long> sorted = memberIdsSortedBy(Comparator.comparingLong(count).reversed()
				.thenComparing(Comparator.comparingLong(amount).reversed()));
		List<Integer> ranks = competitionRanks(sorted, count);
		List<PenaltyRankResponse> result = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Long id = sorted.get(i);
			result.add(new PenaltyRankResponse(ranks.get(i), member(id), count.applyAsLong(id),
					amount.applyAsLong(id)));
		}
		return result;
	}

	private List<BoardGameLoserRankResponse> boardGameLoserRanking() {
		List<BoardGameRecord> withLoser = source.boardGames().stream()
				.filter(record -> record.getLoserMemberId() != null).toList();
		Map<Long, Long> losers = withLoser.stream().collect(groupingBy(BoardGameRecord::getLoserMemberId, counting()));
		Map<Long, Long> plays = withLoser.stream().flatMap(record -> record.getRanks().stream())
				.collect(groupingBy(BoardGameRank::getMemberId, counting()));
		ToLongFunction<Long> loserCount = id -> losers.getOrDefault(id, 0L);
		List<Long> sorted = memberIdsSortedBy(Comparator.comparingLong(loserCount).reversed());
		List<Integer> ranks = competitionRanks(sorted, loserCount);
		List<BoardGameLoserRankResponse> result = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Long id = sorted.get(i);
			result.add(new BoardGameLoserRankResponse(ranks.get(i), member(id), loserCount.applyAsLong(id),
					plays.getOrDefault(id, 0L)));
		}
		return result;
	}

	/**
	 * 그룹 멤버 전원의 노쇼 순위를 구한다. 한 모임에서 여러 차수 노쇼여도 1회로 센다.
	 * 연말 결산의 노쇼왕도 같은 계산을 쓴다.
	 */
	List<NoShowRankResponse> noShowRanking() {
		Map<Long, Long> counts = new HashMap<>();
		membersByMeeting(source.noShows()).values()
				.forEach(memberIds -> memberIds.forEach(id -> counts.merge(id, 1L, Long::sum)));
		ToLongFunction<Long> count = id -> counts.getOrDefault(id, 0L);
		List<Long> sorted = memberIdsSortedBy(Comparator.comparingLong(count).reversed());
		List<Integer> ranks = competitionRanks(sorted, count);
		List<NoShowRankResponse> result = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Long id = sorted.get(i);
			result.add(new NoShowRankResponse(ranks.get(i), member(id), count.applyAsLong(id)));
		}
		return result;
	}

	private List<AttendanceLogResponse> attendanceLog() {
		Map<Long, Set<Long>> attendeesByMeeting = membersByMeeting(source.attendances());
		Map<Long, Set<Long>> noShowsByMeeting = membersByMeeting(source.noShows());
		return source.meetings().stream()
				.map(meeting -> new AttendanceLogResponse(meeting.getId(), meeting.getTitle(), meeting.getStartAt(),
						joinOrdered(attendeesByMeeting.getOrDefault(meeting.getId(), Set.of())),
						joinOrdered(noShowsByMeeting.getOrDefault(meeting.getId(), Set.of()))))
				.toList();
	}

	// 모임 id → 그 모임 응답 행의 회원 id(중복 없음)
	private Map<Long, Set<Long>> membersByMeeting(List<Attendance> attendances) {
		Map<Long, Long> meetingIdByRound = source.rounds().stream()
				.collect(Collectors.toMap(Round::getId, round -> round.getMeeting().getId()));
		return attendances.stream()
				.collect(groupingBy((Attendance a) -> meetingIdByRound.get(a.getRound().getId()),
						Collectors.mapping(a -> a.getMember().getId(), Collectors.toCollection(TreeSet::new))));
	}

	private List<HistoryMemberResponse> joinOrdered(Set<Long> memberIds) {
		return memberIds.stream()
				.sorted(Comparator.comparing((Long id) -> joinOrder.getOrDefault(id, Integer.MAX_VALUE))
						.thenComparing(Function.identity()))
				.map(this::member)
				.toList();
	}

	// 그룹 멤버 전원을 주어진 기준으로 정렬한다. 기준이 같으면 가입 순(안정 정렬)
	private List<Long> memberIdsSortedBy(Comparator<Long> comparator) {
		return source.groupMembers().stream().map(gm -> gm.getMember().getId()).sorted(comparator).toList();
	}

	/**
	 * 정렬된 목록의 경쟁 순위(1, 1, 3)를 구한다. 순위 기준 값이 앞 사람과 같으면 같은 순위다.
	 */
	static <T> List<Integer> competitionRanks(List<T> sorted, ToLongFunction<T> key) {
		List<Integer> ranks = new ArrayList<>(sorted.size());
		for (int i = 0; i < sorted.size(); i++) {
			boolean tied = i > 0 && key.applyAsLong(sorted.get(i)) == key.applyAsLong(sorted.get(i - 1));
			ranks.add(tied ? ranks.get(i - 1) : i + 1);
		}
		return ranks;
	}

	private HistoryMemberResponse member(Long memberId) {
		return HistoryMemberResponse.of(memberId, groupMembers.get(Objects.requireNonNull(memberId)));
	}
}
