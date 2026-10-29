package com.poco7250.moyeo.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.auth.dto.KakaoTokenResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class KakaoOAuthPropertiesTest {

	@Test
	void 시크릿만_넣으면_URI와_타임아웃은_기본값으로_바인딩된다() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
				"kakao.oauth.client-id", "id",
				"kakao.oauth.client-secret", "secret",
				"kakao.oauth.redirect-uri", "http://localhost/callback")));

		KakaoOAuthProperties properties = binder.bind("kakao.oauth", KakaoOAuthProperties.class).get();

		assertThat(properties.clientSecret()).isEqualTo("secret");
		assertThat(properties.tokenUri()).isEqualTo("https://kauth.kakao.com/oauth/token");
		assertThat(properties.userInfoUri()).isEqualTo("https://kapi.kakao.com/v2/user/me");
		assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
		assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
	}

	@Test
	void toString에_clientSecret_원문이_없다() {
		KakaoOAuthProperties properties = new KakaoOAuthProperties("id", "super-secret", "http://localhost/callback",
				"https://kauth.kakao.com/oauth/token", "https://kapi.kakao.com/v2/user/me",
				Duration.ofSeconds(3), Duration.ofSeconds(5));

		assertThat(properties.toString()).doesNotContain("super-secret").contains("clientId=id");
	}

	@Test
	void 토큰_응답_toString에_accessToken_원문이_없다() {
		KakaoTokenResponse response = new KakaoTokenResponse("kakao-access-token", "bearer", 21599L);

		assertThat(response.toString()).doesNotContain("kakao-access-token");
	}
}
