package com.poco7250.moyeo.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class ChangeLogRepositoryTest {

	private static final LocalDateTime AT = LocalDateTime.of(2026, 12, 20, 22, 0);

	@Autowired
	ChangeLogRepository changeLogRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	EntityManager entityManager;

	private Meeting meeting;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L));
	}

	@Test
	@DisplayName("모임의 변경 로그를 시각 순으로 조회한다")
	void findsLogsInOrder() {
		changeLogRepository.save(log(AT.plusMinutes(1), 2L));
		changeLogRepository.save(log(AT, 1L));
		flushAndClear();

		List<ChangeLog> logs = changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());

		assertThat(logs).extracting(ChangeLog::getEntityId).containsExactly(1L, 2L);
		assertThat(logs.getFirst().getEntityType()).isEqualTo(ChangeLogEntityType.EXPENSE);
		assertThat(logs.getFirst().getChangeType()).isEqualTo(ChangeType.UPDATE);
		assertThat(logs.getFirst().getBeforeJson()).isEqualTo("{\"amount\":1000}");
		assertThat(logs.getFirst().getAfterJson()).isEqualTo("{\"amount\":2000}");
		assertThat(logs.getFirst().getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
	}

	@Test
	@DisplayName("행위자와 전/후 JSON은 비워서 저장할 수 있다")
	void savesNullableFields() {
		changeLogRepository.save(ChangeLog.record(meeting, null, AT, ChangeLogEntityType.ATTENDANCE, 1L,
				ChangeType.CREATE, null, null, SettlementStatus.DRAFT));
		flushAndClear();

		ChangeLog found = changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId()).getFirst();

		assertThat(found.getActorId()).isNull();
		assertThat(found.getBeforeJson()).isNull();
		assertThat(found.getAfterJson()).isNull();
	}

	@Test
	@DisplayName("저장된 변경 로그의 값을 바꿔도 DB에 반영되지 않는다")
	void ignoresUpdates() {
		ChangeLog saved = changeLogRepository.save(log(AT, 1L));
		entityManager.flush();

		ReflectionTestUtils.setField(saved, "entityId", 99L);
		flushAndClear();

		ChangeLog found = changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId()).getFirst();
		assertThat(found.getEntityId()).isEqualTo(1L);
	}

	@Test
	@DisplayName("변경 로그 저장소에는 저장·조회 외의 경로가 없다")
	void exposesOnlySaveAndFind() {
		assertThat(CrudRepository.class.isAssignableFrom(ChangeLogRepository.class)).isFalse();
		assertThat(Arrays.stream(ChangeLogRepository.class.getMethods()).map(Method::getName))
				.containsExactlyInAnyOrder("save", "findAllByMeetingIdOrderByChangedAtAscIdAsc");
	}

	private ChangeLog log(LocalDateTime changedAt, Long entityId) {
		return ChangeLog.record(meeting, 1L, changedAt, ChangeLogEntityType.EXPENSE, entityId, ChangeType.UPDATE,
				"{\"amount\":1000}", "{\"amount\":2000}", SettlementStatus.REOPENED);
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
