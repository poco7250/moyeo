package com.poco7250.moyeo.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.support.AuditingTestEntity;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class BaseTimeEntityTest {

	@Autowired
	TestEntityManager em;

	@Test
	void 저장하면_생성_시각과_수정_시각이_채워진다() {
		Long id = em.persistAndFlush(new AuditingTestEntity("모여")).getId();
		em.clear();

		AuditingTestEntity found = em.find(AuditingTestEntity.class, id);

		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	@Test
	void 수정하면_수정_시각만_갱신되고_생성_시각은_그대로다() {
		AuditingTestEntity saved = em.persistAndFlush(new AuditingTestEntity("모여"));
		LocalDateTime createdAt = saved.getCreatedAt();
		LocalDateTime updatedAt = saved.getUpdatedAt();

		saved.rename("모여2");
		em.flush();
		em.clear();
		AuditingTestEntity found = em.find(AuditingTestEntity.class, saved.getId());

		assertThat(found.getName()).isEqualTo("모여2");
		assertThat(found.getCreatedAt()).isEqualTo(createdAt);
		assertThat(found.getUpdatedAt()).isAfter(updatedAt);
	}
}
