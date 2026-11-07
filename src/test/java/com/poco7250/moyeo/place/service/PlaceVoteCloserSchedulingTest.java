package com.poco7250.moyeo.place.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 테스트 프로필은 스케줄을 꺼 두므로, 켰을 때 @Scheduled가 실제로 실행되는지 여기서만 확인한다.
 */
@SpringBootTest(properties = "place.vote-closer.cron=* * * * * *")
@Import(TestcontainersConfiguration.class)
class PlaceVoteCloserSchedulingTest {

	@MockitoSpyBean
	RoundRepository roundRepository;

	@Test
	void 스케줄을_켜면_주기적으로_마감_대상을_조회한다() {
		verify(roundRepository, timeout(5000).atLeastOnce()).findVoteClosableIds(any(LocalDateTime.class));
	}
}
