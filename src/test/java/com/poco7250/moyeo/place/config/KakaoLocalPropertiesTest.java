package com.poco7250.moyeo.place.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class KakaoLocalPropertiesTest {

	@Test
	void 키만_넣으면_URI와_타임아웃은_기본값으로_바인딩된다() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("kakao.local.rest-api-key", "key")));

		KakaoLocalProperties properties = binder.bind("kakao.local", KakaoLocalProperties.class).get();

		assertThat(properties.restApiKey()).isEqualTo("key");
		assertThat(properties.searchUri()).isEqualTo("https://dapi.kakao.com/v2/local/search/keyword.json");
		assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
		assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
	}

	@Test
	void toString에_REST_키_원문이_없다() {
		KakaoLocalProperties properties = new KakaoLocalProperties("super-secret-key",
				"https://dapi.kakao.com/v2/local/search/keyword.json", Duration.ofSeconds(3), Duration.ofSeconds(3));

		assertThat(properties.toString()).doesNotContain("super-secret-key").contains("restApiKey=****");
	}

	@Test
	void 빈도_제한을_안_넣으면_1분에_30회_페이지_15개가_기본값이다() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("place.search.page-size", "15")));

		PlaceSearchProperties properties = binder.bind("place.search", PlaceSearchProperties.class).get();

		assertThat(properties.pageSize()).isEqualTo(15);
		assertThat(properties.rateLimit().maxRequests()).isEqualTo(30);
		assertThat(properties.rateLimit().window()).isEqualTo(Duration.ofMinutes(1));
	}
}
