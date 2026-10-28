package com.poco7250.moyeo.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 테스트용 MySQL 컨테이너 설정.
 * 운영 DB와 같은 MySQL로 DATETIME(6)·NULL 허용 유니크·비관적 락 동작을 검증하기 위해 H2 대신 컨테이너를 쓴다.
 * DB가 필요한 테스트는 이 클래스를 @Import 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * MySQL 컨테이너를 띄우고 접속 정보를 datasource에 자동 연결한다.
	 */
	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer("mysql:8.4");
	}
}
