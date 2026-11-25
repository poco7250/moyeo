package com.poco7250.moyeo.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * V2(Task 074, 게임 → 모임·금액 이전) 마이그레이션의 데이터 변환을 확인한다.
 * 빈 MySQL에 V1까지만 올리고 옛 구조(차수 게임 + penalty_rule)로 데이터를 넣은 뒤 V2를 적용한다.
 * 스프링 컨텍스트 없이 전용 컨테이너를 써서 다른 테스트 스키마와 섞이지 않는다.
 */
class MeetingGameMigrationTest {

	private static final String NOW = "'2026-11-20 20:00:00'";

	private static MySQLContainer mysql;
	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrateV1WithLegacyData() {
		mysql = new MySQLContainer("mysql:8.4");
		mysql.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(),
				mysql.getPassword());
		jdbc = new JdbcTemplate(dataSource);
		flyway(dataSource, "1").migrate();
		insertLegacyData();
		flyway(dataSource, "2").migrate();
	}

	@AfterAll
	static void stop() {
		mysql.stop();
	}

	@Test
	@DisplayName("게임은 차수의 모임으로 옮겨지고 round_id 컬럼과 penalty_rule 테이블은 없어진다")
	void movesGamesToMeeting() {
		assertThat(jdbc.queryForList("select meeting_id from game_session order by id", Long.class))
				.containsOnly(1L).hasSize(4);
		assertThat(columnExists("game_session", "round_id")).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from information_schema.tables "
				+ "where table_schema = database() and table_name = 'penalty_rule'", Integer.class)).isZero();
	}

	@Test
	@DisplayName("고정 금액 조건 게임은 금액을 옮기고 벌칙을 APPLIED로, 비율·조건 없는 금액형 게임은 금액 없이 벌칙을 WAIVED로 둔다")
	void convertsPenaltyAmounts() {
		assertThat(jdbc.queryForObject("select penalty_amount from game_session where id = 10", Long.class))
				.isEqualTo(20_000L);
		assertThat(jdbc.queryForObject("select penalty_amount from game_session where id = 11", Long.class)).isNull();
		assertThat(jdbc.queryForObject("select penalty_amount from game_session where id = 12", Long.class)).isNull();
		assertThat(statusOf(100L)).isEqualTo("APPLIED");
		assertThat(statusOf(101L)).isEqualTo("WAIVED");
		assertThat(statusOf(102L)).isEqualTo("WAIVED");
		// 무효·행동형 벌칙은 그대로다
		assertThat(statusOf(103L)).isEqualTo("VOIDED");
		assertThat(statusOf(104L)).isEqualTo("ASSIGNED");
	}

	@Test
	@DisplayName("리비전 벌칙 스냅샷의 대상 지출 컬럼은 비워 둘 수 있게 된다")
	void relaxesPenaltyChargeColumns() {
		List<Map<String, Object>> columns = jdbc.queryForList("select column_name, is_nullable "
				+ "from information_schema.columns where table_schema = database() and table_name = 'penalty_charge' "
				+ "and column_name in ('target_expense_id', 'target_expense_amount')");
		assertThat(columns).hasSize(2).allSatisfy(column -> assertThat(column.get("IS_NULLABLE")).isEqualTo("YES"));
	}

	private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
		return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
	}

	/**
	 * 모임 1(차수 1·2), 지출 1건, 게임 4개를 넣는다.
	 * 10: 금액형 + 고정 20,000 조건(ASSIGNED였다고 가정) / 11: 금액형 + 비율 조건(APPLIED) /
	 * 12: 금액형 조건 없음(ASSIGNED, VOIDED 1건 포함) / 13: 행동형(ASSIGNED).
	 */
	private static void insertLegacyData() {
		jdbc.update("insert into member (id, guest, created_at, updated_at, nickname, kakao_id) values "
				+ "(1, false, " + NOW + ", " + NOW + ", '철수', 1), (2, false, " + NOW + ", " + NOW + ", '민수', 2)");
		jdbc.update("insert into friend_group (id, created_at, updated_at, name) values (1, " + NOW + ", " + NOW
				+ ", '대학 동기')");
		jdbc.update("insert into meeting (id, created_at, group_id, host_member_id, updated_at, version, share_token, "
				+ "title, date_status) values (1, " + NOW + ", 1, 1, " + NOW + ", 0, 'token', '연말 모임', 'CONFIRMED')");
		jdbc.update("insert into meeting_round (id, seq, created_at, meeting_id, updated_at, version, name, "
				+ "place_status) values (1, 1, " + NOW + ", 1, " + NOW + ", 0, '밥', 'UNDECIDED'), "
				+ "(2, 2, " + NOW + ", 1, " + NOW + ", 0, '볼링', 'UNDECIDED')");
		jdbc.update("insert into expense (id, amount, created_at, payer_id, round_id, updated_at, memo) values "
				+ "(1, 100000, " + NOW + ", 1, 2, " + NOW + ", '볼링')");
		insertGame(10, 1, "MONEY");
		insertGame(11, 2, "MONEY");
		insertGame(12, 2, "MONEY");
		insertGame(13, 1, "ACTION");
		insertPenalty(100, 10, "ASSIGNED");
		insertPenalty(101, 11, "APPLIED");
		insertPenalty(102, 12, "ASSIGNED");
		jdbc.update("insert into penalty (id, created_at, game_session_id, member_id, updated_at, version, status) "
				+ "values (103, " + NOW + ", 12, 1, " + NOW + ", 0, 'VOIDED')");
		insertPenalty(104, 13, "ASSIGNED");
		jdbc.update("insert into penalty_rule (id, amount, ratio_pct, game_session_id, set_by, target_expense_id, "
				+ "updated_at, version, money_type) values (1, 20000, null, 10, 1, 1, " + NOW + ", 0, 'FIXED_AMOUNT'), "
				+ "(2, null, 30, 11, 1, 1, " + NOW + ", 0, 'RATIO')");
	}

	private static void insertGame(long id, long roundId, String penaltyKind) {
		jdbc.update("insert into game_session (id, penalty_count, created_at, created_by, round_id, updated_at, "
				+ "version, penalty_kind, status, type, root_session_id) values (?, 1, " + NOW + ", 1, ?, " + NOW
				+ ", 0, ?, 'REVEALED', 'LADDER', ?)", id, roundId, penaltyKind, id);
	}

	private static void insertPenalty(long id, long gameId, String status) {
		jdbc.update("insert into penalty (id, created_at, game_session_id, member_id, updated_at, version, status) "
				+ "values (?, " + NOW + ", ?, 2, " + NOW + ", 0, ?)", id, gameId, status);
	}

	private static String statusOf(long penaltyId) {
		return jdbc.queryForObject("select status from penalty where id = ?", String.class, penaltyId);
	}

	private static boolean columnExists(String table, String column) {
		return jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = database() "
				+ "and table_name = ? and column_name = ?", Integer.class, table, column) > 0;
	}
}
