package com.poco7250.moyeo.group.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteLinkTest {

	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 8, 12, 0, 0);

	private final InviteLink link = InviteLink.issue(Group.create("대학 동기"), "token", EXPIRES_AT);

	@Test
	@DisplayName("만료 직전에는 쓸 수 있다")
	void usableBeforeExpiry() {
		assertThat(link.isUsable(EXPIRES_AT.minusSeconds(1))).isTrue();
	}

	@Test
	@DisplayName("만료 시각 정각부터는 쓸 수 없다")
	void notUsableAtExpiry() {
		assertThat(link.isUsable(EXPIRES_AT)).isFalse();
	}

	@Test
	@DisplayName("만료 후에는 쓸 수 없다")
	void notUsableAfterExpiry() {
		assertThat(link.isUsable(EXPIRES_AT.plusSeconds(1))).isFalse();
	}

	@Test
	@DisplayName("폐기된 링크는 만료 전이라도 쓸 수 없다")
	void notUsableAfterRevoke() {
		LocalDateTime now = EXPIRES_AT.minusDays(1);

		link.revoke(now);

		assertThat(link.isUsable(now)).isFalse();
		assertThat(link.getRevokedAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("두 번 폐기해도 처음 폐기 시각을 유지한다")
	void revokeTwiceKeepsFirstTime() {
		LocalDateTime first = EXPIRES_AT.minusDays(2);

		link.revoke(first);
		link.revoke(first.plusHours(1));

		assertThat(link.getRevokedAt()).isEqualTo(first);
	}
}
