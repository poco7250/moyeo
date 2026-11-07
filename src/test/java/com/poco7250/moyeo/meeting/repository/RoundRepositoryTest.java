package com.poco7250.moyeo.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.PlaceStatus;
import com.poco7250.moyeo.meeting.domain.Round;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class RoundRepositoryTest {

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	EntityManager entityManager;

	private Meeting meeting;
	private Meeting otherMeeting;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		LocalDateTime startAt = LocalDateTime.of(2026, 12, 20, 18, 0);
		meeting = meetingRepository.saveAndFlush(Meeting.create(group, "연말 모임", startAt, null, null, 1L));
		otherMeeting = meetingRepository.saveAndFlush(Meeting.create(group, "신년 모임", startAt, null, null, 1L));
	}

	@Test
	@DisplayName("모임의 차수를 seq 순서로 조회하고 다른 모임 차수는 제외한다")
	void findAllByMeetingIdOrderBySeq() {
		roundRepository.saveAndFlush(Round.create(meeting, 2, "볼링"));
		roundRepository.saveAndFlush(Round.create(otherMeeting, 1, "카페"));
		roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));

		assertThat(roundRepository.findAllByMeetingIdOrderBySeq(meeting.getId()))
				.extracting(Round::getSeq, Round::getName)
				.containsExactly(tuple(1, "밥"), tuple(2, "볼링"));
	}

	@Test
	@DisplayName("마감 처리 대상은 마감이 지난 투표 차수뿐이고 정각·대기·확정·미정 차수는 제외한다")
	void findVoteClosableIds() {
		LocalDateTime now = LocalDateTime.of(2026, 12, 19, 18, 0);
		Round expired = votingRound(1, now.minusSeconds(1));
		votingRound(2, now);
		votingRound(3, now.plusSeconds(1));
		Round awaiting = votingRound(4, now.minusSeconds(1));
		awaiting.markVoteClosed(now);
		roundRepository.saveAndFlush(awaiting);
		roundRepository.saveAndFlush(Round.create(meeting, 5, "미정"));

		assertThat(roundRepository.findVoteClosableIds(now)).containsExactly(expired.getId());
	}

	@Test
	@DisplayName("마감 처리 조회용 인덱스가 실제 스키마에 만들어진다")
	void voteCloseIndexExists() {
		Number count = (Number) entityManager.createNativeQuery("""
				select count(*) from information_schema.statistics
				where table_schema = database() and table_name = 'meeting_round'
				and index_name = 'idx_meeting_round_place_status_place_vote_deadline'
				""").getSingleResult();

		assertThat(count.intValue()).isEqualTo(2);
	}

	private Round votingRound(int seq, LocalDateTime deadline) {
		Round round = Round.create(meeting, seq, "차수" + seq);
		round.openVoting(deadline);
		return roundRepository.saveAndFlush(round);
	}

	@Test
	@DisplayName("새 차수는 장소 미정 상태이고 version이 0이다")
	void newRoundDefaults() {
		Round round = roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));

		assertThat(round.getPlaceStatus()).isEqualTo(PlaceStatus.UNDECIDED);
		assertThat(round.getVersion()).isZero();
	}

	@Test
	@DisplayName("같은 모임에 같은 seq 차수를 두 번 저장하면 유니크 제약 위반이다")
	void duplicateSeq() {
		roundRepository.saveAndFlush(Round.create(meeting, 1, "밥"));

		assertThatThrownBy(() -> roundRepository.saveAndFlush(Round.create(meeting, 1, "볼링")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
