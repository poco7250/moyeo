package com.poco7250.moyeo;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.support.TestClocks;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MoyeoApplicationTests {

	@Autowired
	Clock clock;

	@Test
	void contextLoads() {
	}

	@Test
	void Clock_빈은_서울_시간대를_쓴다() {
		assertThat(clock.getZone()).isEqualTo(TestClocks.SEOUL);
	}

}
