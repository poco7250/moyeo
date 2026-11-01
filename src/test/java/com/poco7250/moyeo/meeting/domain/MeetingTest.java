package com.poco7250.moyeo.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.group.domain.Group;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeetingTest {

	private final Meeting meeting = Meeting.create(
			Group.create("대학 동기"), "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 7L);

	@Test
	@DisplayName("모임을 만든 회원은 호스트다")
	void creatorIsHost() {
		assertThat(meeting.isHost(7L)).isTrue();
	}

	@Test
	@DisplayName("다른 회원은 호스트가 아니다")
	void otherMemberIsNotHost() {
		assertThat(meeting.isHost(8L)).isFalse();
	}

	@Test
	@DisplayName("회원 id가 null이면 호스트가 아니다")
	void nullIsNotHost() {
		assertThat(meeting.isHost(null)).isFalse();
	}
}
