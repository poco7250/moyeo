package com.poco7250.moyeo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.dto.MemberJoinRequest;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@InjectMocks
	MemberService memberService;

	private MemberJoinRequest request() {
		return new MemberJoinRequest("user@moyeo.com", "password123", "password123", "모여");
	}

	@Test
	void 회원가입하면_비밀번호를_암호화해서_저장한다() {
		given(passwordEncoder.encode("password123")).willReturn("encoded");
		given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

		MemberResponse response = memberService.join(request());

		ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
		verify(memberRepository).save(captor.capture());
		assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
		assertThat(response.email()).isEqualTo("user@moyeo.com");
		assertThat(response.nickname()).isEqualTo("모여");
	}

	@Test
	void 이메일이_중복되면_예외가_발생한다() {
		given(memberRepository.existsByEmail("user@moyeo.com")).willReturn(true);

		assertThatThrownBy(() -> memberService.join(request()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EMAIL);
		verify(memberRepository, never()).save(any());
	}

	@Test
	void 닉네임이_중복되면_예외가_발생한다() {
		given(memberRepository.existsByNickname("모여")).willReturn(true);

		assertThatThrownBy(() -> memberService.join(request()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
		verify(memberRepository, never()).save(any());
	}
}
