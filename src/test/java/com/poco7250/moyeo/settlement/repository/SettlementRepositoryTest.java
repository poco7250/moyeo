package com.poco7250.moyeo.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class SettlementRepositoryTest {

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	GroupRepository groupRepository;

	private Meeting meeting;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L));
	}

	@Test
	@DisplayName("한 모임에 정산을 두 건 저장하면 유니크 제약 위반이다")
	void duplicateSettlement() {
		settlementRepository.saveAndFlush(Settlement.createDraft(meeting));

		assertThatThrownBy(() -> settlementRepository.saveAndFlush(Settlement.createDraft(meeting)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("새 정산은 DRAFT이고 리비전이 없으며 version이 0이다")
	void newSettlementDefaults() {
		Settlement settlement = settlementRepository.saveAndFlush(Settlement.createDraft(meeting));

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.DRAFT);
		assertThat(settlement.getCurrentRevisionNo()).isNull();
		assertThat(settlement.getFirstConfirmedAt()).isNull();
		assertThat(settlement.getVersion()).isZero();
	}
}
