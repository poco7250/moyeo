package com.poco7250.moyeo.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class MeetingRepositoryTest {

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	GroupRepository groupRepository;

	@Test
	@DisplayName("모임 id로 그룹 id만 조회하고, 없는 모임은 비어 있다")
	void findGroupIdById() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), "메모", null, 1L));

		assertThat(meetingRepository.findGroupIdById(meeting.getId())).hasValue(group.getId());
		assertThat(meetingRepository.findGroupIdById(9999L)).isEmpty();
	}

	@Test
	@DisplayName("다가오는 모임은 기준 시각 정각을 포함해 가까운 순, 지난 모임은 최근 순이고 다른 그룹 모임은 빠진다")
	void upcomingAndPast() {
		LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Group other = groupRepository.saveAndFlush(Group.create("회사"));
		Meeting later = save(group, "다음 달", now.plusDays(30));
		Meeting atNow = save(group, "지금", now);
		Meeting yesterday = save(group, "어제", now.minusDays(1));
		Meeting lastWeek = save(group, "지난주", now.minusDays(7));
		save(other, "다른 그룹", now.plusDays(1));

		assertThat(meetingRepository.findAllByGroupIdAndStartAtGreaterThanEqualOrderByStartAtAsc(group.getId(), now))
				.containsExactly(atNow, later);
		assertThat(meetingRepository.findAllByGroupIdAndStartAtLessThanOrderByStartAtDesc(group.getId(), now))
				.containsExactly(yesterday, lastWeek);
	}

	private Meeting save(Group group, String title, LocalDateTime startAt) {
		return meetingRepository.saveAndFlush(Meeting.create(group, title, startAt, null, null, 1L));
	}
}
