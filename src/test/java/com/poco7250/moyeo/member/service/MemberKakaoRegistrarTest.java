package com.poco7250.moyeo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.dto.KakaoProfile;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberKakaoRegistrarTest {

	@Mock
	MemberRepository memberRepository;

	@InjectMocks
	MemberKakaoRegistrar memberKakaoRegistrar;

	@Test
	void 처음_보는_kakaoId면_회원을_새로_만든다() {
		KakaoProfile profile = new KakaoProfile(1001L, "모여", "https://img.kakao.com/a.png", null);
		given(memberRepository.findByKakaoId(1001L)).willReturn(Optional.empty());
		given(memberRepository.saveAndFlush(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

		MemberResponse response = memberKakaoRegistrar.registerOrUpdate(profile);

		ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
		verify(memberRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getKakaoId()).isEqualTo(1001L);
		assertThat(captor.getValue().getEmail()).isNull();
		assertThat(response.nickname()).isEqualTo("모여");
		assertThat(response.profileImageUrl()).isEqualTo("https://img.kakao.com/a.png");
	}

	@Test
	void 이미_있는_kakaoId면_저장_없이_프로필만_갱신한다() {
		Member existing = Member.ofKakao(1001L, "옛닉네임", null, "old@kakao.com");
		given(memberRepository.findByKakaoId(1001L)).willReturn(Optional.of(existing));

		MemberResponse response = memberKakaoRegistrar.registerOrUpdate(
				new KakaoProfile(1001L, "새닉네임", "https://img.kakao.com/b.png", null));

		verify(memberRepository, never()).saveAndFlush(any());
		assertThat(existing.getNickname()).isEqualTo("새닉네임");
		assertThat(existing.getProfileImageUrl()).isEqualTo("https://img.kakao.com/b.png");
		assertThat(existing.getEmail()).isNull();
		assertThat(response.nickname()).isEqualTo("새닉네임");
	}

	@Test
	void 닉네임이_없으면_기본_닉네임으로_새로_만든다() {
		given(memberRepository.findByKakaoId(1001L)).willReturn(Optional.empty());
		given(memberRepository.saveAndFlush(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

		MemberResponse response = memberKakaoRegistrar.registerOrUpdate(new KakaoProfile(1001L, null, null, null));

		assertThat(response.nickname()).isEqualTo(Member.DEFAULT_NICKNAME);
	}

	@Test
	void 닉네임이_공백뿐이면_기본_닉네임으로_새로_만든다() {
		given(memberRepository.findByKakaoId(1001L)).willReturn(Optional.empty());
		given(memberRepository.saveAndFlush(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

		MemberResponse response = memberKakaoRegistrar.registerOrUpdate(new KakaoProfile(1001L, "  ", null, null));

		assertThat(response.nickname()).isEqualTo(Member.DEFAULT_NICKNAME);
	}

	@Test
	void 기존_회원이_닉네임_동의를_철회하면_원래_닉네임을_유지한다() {
		Member existing = Member.ofKakao(1001L, "옛닉네임", "https://img.kakao.com/a.png", null);
		given(memberRepository.findByKakaoId(1001L)).willReturn(Optional.of(existing));

		MemberResponse response = memberKakaoRegistrar.registerOrUpdate(new KakaoProfile(1001L, null, null, null));

		assertThat(existing.getNickname()).isEqualTo("옛닉네임");
		assertThat(existing.getProfileImageUrl()).isNull();
		assertThat(response.nickname()).isEqualTo("옛닉네임");
	}
}
