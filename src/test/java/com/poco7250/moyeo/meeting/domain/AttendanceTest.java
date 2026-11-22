package com.poco7250.moyeo.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.member.domain.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AttendanceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 18, 0);

	private final Round round = Round.create(
			Meeting.create(Group.create("대학 동기"), "연말 모임", NOW, null, null, 1L), 1, "밥");
	private final Member member = Member.ofKakao(2L, "민수", null, null);

	@Test
	@DisplayName("참석으로 응답하고 체크인하지 않았으면 노쇼로 남고, 불참으로 바뀌어도 유지된다")
	void attendWithoutCheckInIsNoShow() {
		Attendance attendance = Attendance.respond(round, member, AttendanceStatus.ATTEND, NOW);

		attendance.recordNoShowIfMissed();
		attendance.changeStatus(AttendanceStatus.ABSENT, NOW);
		attendance.recordNoShowIfMissed();

		assertThat(attendance.isNoShow()).isTrue();
	}

	@Test
	@DisplayName("참석 응답이어도 체크인했으면 노쇼가 아니다")
	void checkedInIsNotNoShow() {
		Attendance attendance = Attendance.respond(round, member, AttendanceStatus.ATTEND, NOW);
		attendance.changeCheckIn(true);

		attendance.recordNoShowIfMissed();

		assertThat(attendance.isNoShow()).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = AttendanceStatus.class, names = {"ABSENT", "UNDECIDED"})
	@DisplayName("불참·미정 응답은 체크인하지 않아도 노쇼가 아니다")
	void notAttendingIsNotNoShow(AttendanceStatus status) {
		Attendance attendance = Attendance.respond(round, member, status, NOW);

		attendance.recordNoShowIfMissed();

		assertThat(attendance.isNoShow()).isFalse();
	}

	@Test
	@DisplayName("노쇼인 응답이 다시 참석으로 바뀌면 노쇼가 풀린다")
	void attendClearsNoShow() {
		Attendance attendance = Attendance.respond(round, member, AttendanceStatus.ATTEND, NOW);
		attendance.recordNoShowIfMissed();
		attendance.changeStatus(AttendanceStatus.ABSENT, NOW);

		attendance.changeStatus(AttendanceStatus.ATTEND, NOW.plusHours(1));

		assertThat(attendance.isNoShow()).isFalse();
	}
}
