package com.poco7250.moyeo.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.place.domain.Place;
import com.poco7250.moyeo.place.domain.PlaceCandidate;
import com.poco7250.moyeo.place.repository.PlaceCandidateRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUtil;
import jakarta.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 공유 링크·모임 상세에서 쓰는 조회를 실제 DB로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class MeetingShareQueriesTest {

	private static final LocalDateTime START_AT = LocalDateTime.of(2026, 12, 19, 18, 0);

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	PlaceCandidateRepository placeCandidateRepository;

	@Autowired
	EntityManager entityManager;

	private Group group;
	private Member member;
	private Meeting meeting;

	@BeforeEach
	void setUp() {
		group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		member = memberRepository.saveAndFlush(Member.ofKakao(5001L, "호스트", null, null));
		meeting = meetingRepository.saveAndFlush(Meeting.create(group, "연말 모임", START_AT, null, null, member.getId()));
	}

	@Test
	@DisplayName("공유 토큰으로 모임을 찾고 없는 토큰은 비어 있다")
	void findByShareToken() {
		assertThat(meeting.getShareToken()).hasSize(43);
		assertThat(meetingRepository.findByShareToken(meeting.getShareToken())).hasValue(meeting);
		assertThat(meetingRepository.findByShareToken("없는토큰")).isEmpty();
	}

	@Test
	@DisplayName("공유 토큰이 겹치면 유니크 제약 위반이다")
	void shareTokenUnique() {
		Meeting duplicate = Meeting.create(group, "신년 모임", START_AT, null, null, member.getId());
		ReflectionTestUtils.setField(duplicate, "shareToken", meeting.getShareToken());

		assertThatThrownBy(() -> meetingRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("차수를 seq 순으로 확정 후보와 함께 읽고 미확정 차수도 포함한다")
	void findAllWithConfirmedPlace() {
		Round second = roundRepository.saveAndFlush(Round.create(meeting, 2, "볼링"));
		Round first = roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));
		PlaceCandidate candidate = placeCandidateRepository.saveAndFlush(
				PlaceCandidate.propose(first, Place.of("강남 식당", null, null), member));
		first.confirmDirectly(candidate);
		roundRepository.saveAndFlush(first);
		entityManager.clear();

		List<Round> rounds = roundRepository.findAllWithConfirmedPlaceByMeetingId(meeting.getId());

		assertThat(rounds).extracting(Round::getId).containsExactly(first.getId(), second.getId());
		PersistenceUtil persistence = Persistence.getPersistenceUtil();
		assertThat(persistence.isLoaded(rounds.getFirst().getConfirmedCandidate())).isTrue();
		assertThat(rounds.getFirst().confirmedPlace().getLabel()).isEqualTo("강남 식당");
		assertThat(rounds.get(1).confirmedPlace()).isNull();
	}

	@Test
	@DisplayName("차수별 참석 인원은 해당 상태만 센다")
	void countByRoundIdAndStatus() {
		Round round = roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));
		Member absent = memberRepository.saveAndFlush(Member.ofKakao(5002L, "불참", null, null));
		attendanceRepository.saveAndFlush(Attendance.respond(round, member, AttendanceStatus.ATTEND, START_AT));
		attendanceRepository.saveAndFlush(Attendance.respond(round, absent, AttendanceStatus.ABSENT, START_AT));

		assertThat(attendanceRepository.countByRoundIdAndStatus(round.getId(), AttendanceStatus.ATTEND)).isEqualTo(1);
	}
}
