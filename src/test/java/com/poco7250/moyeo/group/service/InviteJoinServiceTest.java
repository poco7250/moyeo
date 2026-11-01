package com.poco7250.moyeo.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.group.dto.InviteJoinResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class InviteJoinServiceTest {

	private static final String TOKEN = "t".repeat(43);

	@Mock
	InviteJoiner inviteJoiner;

	@InjectMocks
	InviteJoinService inviteJoinService;

	@Test
	void 가입_결과를_그대로_준다() {
		given(inviteJoiner.join(TOKEN, 7L)).willReturn(new InviteJoinResponse(1L, true));

		assertThat(inviteJoinService.join(TOKEN, 7L)).isEqualTo(new InviteJoinResponse(1L, true));
		verify(inviteJoiner, times(1)).join(TOKEN, 7L);
	}

	@Test
	void 동시_클릭으로_유니크_위반이_나면_다시_확인해_기존_멤버로_성공한다() {
		given(inviteJoiner.join(TOKEN, 7L))
				.willThrow(new DataIntegrityViolationException("uk_group_member_group_id_member_id"))
				.willReturn(new InviteJoinResponse(1L, false));

		assertThat(inviteJoinService.join(TOKEN, 7L)).isEqualTo(new InviteJoinResponse(1L, false));
		verify(inviteJoiner, times(2)).join(TOKEN, 7L);
	}

	@Test
	void 재시도도_실패하면_예외를_그대로_던진다() {
		given(inviteJoiner.join(TOKEN, 7L))
				.willThrow(new DataIntegrityViolationException("first"))
				.willThrow(new DataIntegrityViolationException("second"));

		assertThatThrownBy(() -> inviteJoinService.join(TOKEN, 7L))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
