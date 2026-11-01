package com.poco7250.moyeo.group.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class InviteLinkPropertiesTest {

	@Test
	@DisplayName("아무 설정이 없으면 만료 7일, 상대 경로 URL이 기본값이다")
	void defaults() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of()));

		InviteLinkProperties properties = binder.bindOrCreate("group.invite", InviteLinkProperties.class);

		assertThat(properties.ttl()).isEqualTo(Duration.ofDays(7));
		assertThat(properties.urlOf("abc")).isEqualTo("/invites/abc");
	}

	@Test
	@DisplayName("base-url을 넣으면 그 주소 뒤에 초대 경로를 붙인다")
	void baseUrl() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
				"group.invite.ttl", "3d",
				"group.invite.base-url", "https://moyeo.example.com")));

		InviteLinkProperties properties = binder.bind("group.invite", InviteLinkProperties.class).get();

		assertThat(properties.ttl()).isEqualTo(Duration.ofDays(3));
		assertThat(properties.urlOf("abc")).isEqualTo("https://moyeo.example.com/invites/abc");
	}
}
