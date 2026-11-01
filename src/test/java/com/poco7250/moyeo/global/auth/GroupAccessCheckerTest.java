package com.poco7250.moyeo.global.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupAccessCheckerTest {

	@Mock
	GroupMemberRepository groupMemberRepository;

	@InjectMocks
	GroupAccessChecker groupAccessChecker;

	@Test
	void 그룹_멤버면_통과한다() {
		given(groupMemberRepository.existsByGroupIdAndMemberId(1L, 7L)).willReturn(true);

		assertThatCode(() -> groupAccessChecker.requireMember(1L, 7L)).doesNotThrowAnyException();
	}

	@Test
	void 비멤버면_NOT_GROUP_MEMBER() {
		given(groupMemberRepository.existsByGroupIdAndMemberId(1L, 8L)).willReturn(false);

		assertThatThrownBy(() -> groupAccessChecker.requireMember(1L, 8L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
	}
}
