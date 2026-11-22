package com.poco7250.moyeo.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.member.domain.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeetingGuestTest {

	private final Meeting meeting = Meeting.create(Group.create("대학 동기"), "연말 모임",
			LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L);

	@Test
	@DisplayName("게스트 회원은 이름 앞뒤 공백을 빼고 카카오 id 없이 만들어지고, 모임에 묶으면 이름이 그대로 보인다")
	void createsGuest() {
		Member guest = Member.guest("  민수 친구 ");

		MeetingGuest meetingGuest = MeetingGuest.add(meeting, guest, 1L);

		assertThat(guest.isGuest()).isTrue();
		assertThat(guest.getKakaoId()).isNull();
		assertThat(meetingGuest.name()).isEqualTo("민수 친구");
		assertThat(meetingGuest.getCreatedBy()).isEqualTo(1L);
	}

	@Test
	@DisplayName("게스트 이름이 비어 있거나, 카카오 회원을 게스트로 묶으면 예외다")
	void rejectsInvalid() {
		assertThatThrownBy(() -> Member.guest("  ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MeetingGuest.add(meeting, Member.ofKakao(1L, "회원", null, null), 1L))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
