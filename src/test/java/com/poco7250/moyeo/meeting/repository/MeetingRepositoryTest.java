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
}
