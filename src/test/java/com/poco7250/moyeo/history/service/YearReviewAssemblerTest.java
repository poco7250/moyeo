package com.poco7250.moyeo.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.history.dto.YearReviewResponse;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class YearReviewAssemblerTest {

	private final Group group = Group.create("대학 동기");
	private final Member host = member(1L, "호스트");
	private final List<Meeting> meetings = new ArrayList<>();
	private final List<Round> rounds = new ArrayList<>();

	@Test
	@DisplayName("장소는 방문 횟수 → 최근 방문 순으로 정렬하고 횟수가 같으면 같은 순위, 앞에서 3개만 준다")
	void topPlaces() {
		visit("A식당", 1);
		visit("B포차", 2);
		visit("A식당", 3);
		visit("B포차", 6);
		visit("D카페", 11);
		visit("C횟집", 12);

		YearReviewResponse result = assemble(Map.of());

		assertThat(result.topPlaces()).extracting(p -> p.rank(), p -> p.label(), p -> p.visitCount())
				.containsExactly(tuple(1, "B포차", 2L), tuple(1, "A식당", 2L), tuple(3, "C횟집", 1L));
		assertThat(result.shareText()).contains("가장 많이 간 곳: B포차(2번)");
	}

	@Test
	@DisplayName("참석·벌칙이 없으면 벌칙왕은 비고 공유 문구에서 그 줄들이 빠진다. 부담액은 금액 순 경쟁 순위다")
	void noAttendanceOrPenalty() {
		Member minsu = member(2L, "민수");
		Member younghee = member(3L, "영희");
		visit("A식당", 5);

		YearReviewResponse result = assemble(Map.of(2L, 10_000L, 3L, 10_000L), minsu, younghee);

		assertThat(result.penaltyKings()).isEmpty();
		assertThat(result.shareRanking()).extracting(r -> r.member().nickname(), r -> r.rank())
				.containsExactly(tuple("민수", 1), tuple("영희", 1), tuple("호스트", 3));
		assertThat(result.noShowKings()).isEmpty();
		assertThat(result.shareText()).doesNotContain("최다 참석", "벌칙왕", "노쇼왕");
	}

	@Test
	@DisplayName("노쇼는 모임 단위로 센다(한 모임 두 차수 노쇼 = 1회). 노쇼왕은 1위 전원이고 공유 문구에 들어간다")
	void noShowsCountPerMeeting() {
		Member minsu = member(2L, "민수");
		Member younghee = member(3L, "영희");
		Round march = visit("A식당", 3);
		Round marchSecond = secondRound(march);
		Round may = visit("B포차", 5);
		List<Attendance> noShows = List.of(noShow(march, minsu), noShow(marchSecond, minsu), noShow(may, younghee));

		YearReviewSource source = source(Map.of(), noShows, minsu, younghee);
		YearReviewResponse result = new YearReviewAssembler(source).assemble(2019);

		assertThat(new HallOfFameAssembler(source.toHallOfFameSource()).noShowRanking())
				.extracting(r -> r.member().nickname(), r -> r.rank(), r -> r.noShowCount())
				.containsExactly(tuple("민수", 1, 1L), tuple("영희", 1, 1L), tuple("호스트", 3, 0L));
		assertThat(result.noShowKings()).extracting(r -> r.member().nickname()).containsExactly("민수", "영희");
		assertThat(result.shareText()).contains("노쇼왕: 민수·영희(1번)");
	}

	private YearReviewResponse assemble(Map<Long, Long> shareAmounts, Member... others) {
		return new YearReviewAssembler(source(shareAmounts, List.of(), others)).assemble(2019);
	}

	private YearReviewSource source(Map<Long, Long> shareAmounts, List<Attendance> noShows, Member... others) {
		List<GroupMember> groupMembers = new ArrayList<>(List.of(GroupMember.join(group, host, "호스트",
				GroupRole.OWNER)));
		for (Member other : others) {
			groupMembers.add(GroupMember.join(group, other, other.getNickname(), GroupRole.MEMBER));
		}
		return new YearReviewSource("대학 동기", meetings, rounds, List.of(), noShows, 0L, 0L, List.of(), Map.of(),
				shareAmounts, groupMembers);
	}

	// 참석으로 응답하고 체크인하지 않은 채 실제 참석자가 확정된 행
	private static Attendance noShow(Round round, Member member) {
		LocalDateTime startAt = round.getMeeting().getStartAt();
		Attendance attendance = Attendance.respond(round, member, AttendanceStatus.ATTEND, startAt);
		attendance.recordNoShowIfMissed();
		attendance.changeStatus(AttendanceStatus.ABSENT, startAt);
		return attendance;
	}

	// 같은 모임의 2차(장소 미정)
	private Round secondRound(Round first) {
		Round round = Round.create(first.getMeeting(), 2, "2차");
		ReflectionTestUtils.setField(round, "id", first.getId() + 100);
		rounds.add(round);
		return round;
	}

	// month월에 시작한 1차짜리 모임의 장소를 확정한다
	private Round visit(String label, int month) {
		Meeting meeting = Meeting.create(group, month + "월 모임", LocalDateTime.of(2019, month, 1, 18, 0), null,
				null, host.getId());
		ReflectionTestUtils.setField(meeting, "id", (long) month);
		Round round = Round.create(meeting, 1, "밥");
		ReflectionTestUtils.setField(round, "id", (long) month);
		round.confirmDirectly(PlaceCandidate.propose(round, Place.of(label, null, null), host));
		meetings.add(meeting);
		rounds.add(round);
		return round;
	}

	private static Member member(Long id, String nickname) {
		Member member = Member.ofKakao(id, nickname, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
