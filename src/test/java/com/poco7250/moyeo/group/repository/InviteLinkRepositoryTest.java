package com.poco7250.moyeo.group.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.InviteLink;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class InviteLinkRepositoryTest {

	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 8, 12, 0, 0);
	private static final String TOKEN = "a".repeat(43);

	@Autowired
	InviteLinkRepository inviteLinkRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	EntityManager entityManager;

	private Group group;

	@BeforeEach
	void setUp() {
		group = groupRepository.saveAndFlush(Group.create("대학 동기"));
	}

	@Test
	@DisplayName("같은 토큰을 두 번 저장하면 유니크 제약 위반이다")
	void duplicateToken() {
		inviteLinkRepository.saveAndFlush(InviteLink.issue(group, TOKEN, EXPIRES_AT));

		assertThatThrownBy(() -> inviteLinkRepository.saveAndFlush(InviteLink.issue(group, TOKEN, EXPIRES_AT)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("토큰으로 초대 링크를 조회한다")
	void findByToken() {
		inviteLinkRepository.saveAndFlush(InviteLink.issue(group, TOKEN, EXPIRES_AT));

		assertThat(inviteLinkRepository.findByToken(TOKEN))
				.hasValueSatisfying(link -> {
					assertThat(link.getGroup().getId()).isEqualTo(group.getId());
					assertThat(link.getExpiresAt()).isEqualTo(EXPIRES_AT);
					assertThat(link.getRevokedAt()).isNull();
					assertThat(link.getCreatedAt()).isNotNull();
				});
	}

	@Test
	@DisplayName("토큰으로 초대 링크를 그룹과 함께 조회한다")
	void findWithGroupByToken() {
		inviteLinkRepository.saveAndFlush(InviteLink.issue(group, TOKEN, EXPIRES_AT));
		entityManager.clear();

		assertThat(inviteLinkRepository.findWithGroupByToken(TOKEN))
				.hasValueSatisfying(link -> {
					assertThat(Hibernate.isInitialized(link.getGroup())).isTrue();
					assertThat(link.getGroup().getName()).isEqualTo("대학 동기");
				});
		assertThat(inviteLinkRepository.findWithGroupByToken("b".repeat(43))).isEmpty();
	}

	@Test
	@DisplayName("없는 토큰이면 빈 Optional이다")
	void findByUnknownToken() {
		assertThat(inviteLinkRepository.findByToken("b".repeat(43))).isEmpty();
	}

	@Test
	@DisplayName("그룹의 폐기되지 않은 링크만 조회한다(다른 그룹 링크 제외)")
	void findAllByGroupIdAndRevokedAtIsNull() {
		Group otherGroup = groupRepository.saveAndFlush(Group.create("회사 동기"));
		InviteLink revoked = InviteLink.issue(group, "r".repeat(43), EXPIRES_AT);
		revoked.revoke(EXPIRES_AT.minusDays(1));
		inviteLinkRepository.saveAndFlush(revoked);
		inviteLinkRepository.saveAndFlush(InviteLink.issue(group, TOKEN, EXPIRES_AT));
		inviteLinkRepository.saveAndFlush(InviteLink.issue(otherGroup, "o".repeat(43), EXPIRES_AT));

		assertThat(inviteLinkRepository.findAllByGroupIdAndRevokedAtIsNull(group.getId()))
				.extracting(InviteLink::getToken)
				.containsExactly(TOKEN);
	}
}
