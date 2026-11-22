package com.poco7250.moyeo.history.service;

import static java.util.stream.Collectors.groupingBy;

import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.history.dto.AttendanceRankResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.PenaltyRankResponse;
import com.poco7250.moyeo.history.dto.PlaceVisitResponse;
import com.poco7250.moyeo.history.dto.ShareRankResponse;
import com.poco7250.moyeo.history.dto.YearReviewResponse;
import com.poco7250.moyeo.history.service.YearReviewShareText.Ranked;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.place.domain.Place;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * 읽어 둔 데이터로 연말 결산 응답을 조립한다(조회 없음). 순위는 동점이면 같은 순위(1, 1, 3)를 준다.
 */
final class YearReviewAssembler {

	static final int TOP_PLACE_LIMIT = 3;

	private final YearReviewSource source;
	private final Map<Long, GroupMember> groupMembers = new HashMap<>();

	YearReviewAssembler(YearReviewSource source) {
		this.source = source;
		source.groupMembers().forEach(gm -> groupMembers.put(gm.getMember().getId(), gm));
	}

	/**
	 * 응답을 조립한다.
	 */
	YearReviewResponse assemble(int year) {
		List<AttendanceRankResponse> attendance = attendanceRanking();
		List<PlaceVisitResponse> places = topPlaces();
		List<PenaltyRankResponse> kings = penaltyKings();
		String shareText = source.meetings().isEmpty() ? null
				: YearReviewShareText.of(source.groupName(), year, source.meetings().size(), source.totalExpense(),
						topAttendees(attendance), topPlace(places), kingsOf(kings));
		return new YearReviewResponse(year, source.meetings().size(), source.rounds().size(), source.totalExpense(),
				source.confirmedMeetingCount(), attendance, shareRanking(), places, kings, shareText);
	}

	private List<AttendanceRankResponse> attendanceRanking() {
		Map<Long, Long> meetingIdByRound = source.rounds().stream()
				.collect(Collectors.toMap(Round::getId, round -> round.getMeeting().getId()));
		Map<Long, Set<Long>> meetingsByMember = source.attendances().stream()
				.collect(groupingBy((Attendance a) -> a.getMember().getId(),
						Collectors.mapping(a -> meetingIdByRound.get(a.getRound().getId()), Collectors.toSet())));
		ToLongFunction<Long> count = id -> meetingsByMember.getOrDefault(id, Set.of()).size();
		List<Long> sorted = memberIdsSortedBy(count);
		List<Integer> ranks = HallOfFameAssembler.competitionRanks(sorted, count);
		List<AttendanceRankResponse> result = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Long id = sorted.get(i);
			result.add(new AttendanceRankResponse(ranks.get(i), member(id), count.applyAsLong(id)));
		}
		return result;
	}

	private List<ShareRankResponse> shareRanking() {
		ToLongFunction<Long> amount = id -> source.shareAmounts().getOrDefault(id, 0L);
		List<Long> sorted = memberIdsSortedBy(amount);
		List<Integer> ranks = HallOfFameAssembler.competitionRanks(sorted, amount);
		List<ShareRankResponse> result = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			Long id = sorted.get(i);
			result.add(new ShareRankResponse(ranks.get(i), member(id), amount.applyAsLong(id)));
		}
		return result;
	}

	private List<PlaceVisitResponse> topPlaces() {
		List<PlaceVisit> visits = placeVisits().values().stream()
				.sorted(Comparator.comparingLong(PlaceVisit::count).reversed()
						.thenComparing(PlaceVisit::lastVisitedAt, Comparator.reverseOrder()))
				.toList();
		List<Integer> ranks = HallOfFameAssembler.competitionRanks(visits, PlaceVisit::count);
		List<PlaceVisitResponse> result = new ArrayList<>();
		for (int i = 0; i < Math.min(visits.size(), TOP_PLACE_LIMIT); i++) {
			PlaceVisit visit = visits.get(i);
			result.add(new PlaceVisitResponse(ranks.get(i), visit.label(), visit.count(), visit.lastVisitedAt()));
		}
		return result;
	}

	// 앞뒤 공백을 뺀 label → 방문 집계. 표시 label·최근 방문은 가장 최근 모임 기준
	private Map<String, PlaceVisit> placeVisits() {
		Map<Long, LocalDateTime> startAtByMeeting = source.meetings().stream()
				.collect(Collectors.toMap(Meeting::getId, Meeting::getStartAt));
		Map<String, PlaceVisit> visits = new HashMap<>();
		for (Round round : source.rounds()) {
			Place place = round.confirmedPlace();
			String label = place == null || place.getLabel() == null ? "" : place.getLabel().strip();
			if (!label.isEmpty()) {
				PlaceVisit visit = new PlaceVisit(label, 1, startAtByMeeting.get(round.getMeeting().getId()));
				visits.merge(label, visit, PlaceVisit::plus);
			}
		}
		return visits;
	}

	private List<PenaltyRankResponse> penaltyKings() {
		return new HallOfFameAssembler(source.toHallOfFameSource()).penaltyRanking().stream()
				.filter(rank -> rank.rank() == 1 && rank.penaltyCount() > 0)
				.toList();
	}

	private static Ranked topAttendees(List<AttendanceRankResponse> ranking) {
		List<AttendanceRankResponse> top = ranking.stream().filter(r -> r.rank() == 1 && r.attendCount() > 0)
				.toList();
		return top.isEmpty() ? Ranked.NONE
				: new Ranked(top.stream().map(r -> r.member().nickname()).toList(), top.getFirst().attendCount());
	}

	private static Ranked topPlace(List<PlaceVisitResponse> places) {
		return places.isEmpty() ? Ranked.NONE
				: new Ranked(List.of(places.getFirst().label()), places.getFirst().visitCount());
	}

	private static Ranked kingsOf(List<PenaltyRankResponse> kings) {
		return kings.isEmpty() ? Ranked.NONE
				: new Ranked(kings.stream().map(k -> k.member().nickname()).toList(), kings.getFirst().penaltyCount());
	}

	// 그룹 멤버 전원을 값이 큰 순으로 정렬한다. 값이 같으면 가입 순(안정 정렬)
	private List<Long> memberIdsSortedBy(ToLongFunction<Long> value) {
		return source.groupMembers().stream().map(gm -> gm.getMember().getId())
				.sorted(Comparator.comparingLong(value).reversed()).toList();
	}

	private HistoryMemberResponse member(Long memberId) {
		return HistoryMemberResponse.of(memberId, groupMembers.get(Objects.requireNonNull(memberId)));
	}

	/**
	 * 장소 하나의 방문 집계.
	 */
	private record PlaceVisit(String label, long count, LocalDateTime lastVisitedAt) {

		// 횟수를 더하고, 더 최근 방문의 label·시각을 남긴다
		PlaceVisit plus(PlaceVisit other) {
			PlaceVisit recent = other.lastVisitedAt.isAfter(lastVisitedAt) ? other : this;
			return new PlaceVisit(recent.label, count + other.count, recent.lastVisitedAt);
		}
	}
}
