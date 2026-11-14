package com.poco7250.moyeo.support;

import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * API로 데이터를 만드는 통합 테스트가 끝난 뒤 테스트 DB의 모든 테이블을 비운다.
 * 테스트 트랜잭션을 쓰지 않는(실제 커밋을 보는) 시나리오 테스트에서 {@code @AfterEach}로 부른다.
 * 테스트는 순차 실행이라 다른 테스트의 데이터를 지울 일은 없다.
 */
public final class DatabaseCleaner {

	private static final String TABLES_SQL = "select table_name from information_schema.tables "
			+ "where table_schema = database() and table_type = 'BASE TABLE'";

	private final JdbcTemplate jdbcTemplate;

	public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 현재 스키마의 모든 테이블을 TRUNCATE 한다. FK 검사는 같은 커넥션에서만 잠시 끈다.
	 */
	public void truncateAll() {
		List<String> tables = jdbcTemplate.queryForList(TABLES_SQL, String.class);
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				for (String table : tables) {
					statement.execute("TRUNCATE TABLE `" + table + "`");
				}
				statement.execute("SET FOREIGN_KEY_CHECKS = 1");
			}
			return null;
		});
	}
}
