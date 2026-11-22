package com.poco7250.moyeo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 운영 스키마 점검(Task 067): 빈 MySQL에 Flyway 마이그레이션을 돌린 뒤 Hibernate validate가 통과하는지 본다.
 * 다른 테스트는 create-drop으로 스키마를 만들기 때문에, 엔티티를 바꾸고 마이그레이션을 빠뜨리면 이 테스트만 깨진다.
 * 속성이 달라 컨텍스트(와 MySQL 컨테이너)가 따로 뜨므로 다른 테스트 스키마와 섞이지 않는다.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		// 테스트 전용 엔티티(AuditingTestEntity) 테이블은 테스트 리소스의 마이그레이션으로 붙인다
		"spring.flyway.locations=classpath:db/migration,classpath:db/test-migration"
})
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest {

	@Autowired
	Flyway flyway;

	@Test
	@DisplayName("빈 DB에 마이그레이션이 모두 성공하고, 엔티티 매핑과 스키마가 일치해 컨텍스트가 뜬다")
	void migrationsMatchEntities() {
		MigrationInfo[] applied = flyway.info().applied();

		assertThat(applied).isNotEmpty();
		assertThat(Arrays.stream(applied).map(info -> info.getVersion().getVersion())).contains("1");
		assertThat(flyway.info().pending()).isEmpty();
	}
}
