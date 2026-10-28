package com.poco7250.moyeo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.member.dto.KakaoProfile;
import com.poco7250.moyeo.member.dto.MemberResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	@Mock
	MemberKakaoRegistrar memberKakaoRegistrar;

	@InjectMocks
	MemberService memberService;

	private final KakaoProfile profile = new KakaoProfile(1001L, "모여", "https://img.kakao.com/a.png", null);
	private final MemberResponse member = new MemberResponse(1L, "모여", "https://img.kakao.com/a.png");

	@Test
	void 카카오_회원_조회_생성을_registrar에_위임한다() {
		given(memberKakaoRegistrar.registerOrUpdate(profile)).willReturn(member);

		assertThat(memberService.findOrCreateByKakaoId(profile)).isEqualTo(member);
		verify(memberKakaoRegistrar, times(1)).registerOrUpdate(profile);
	}

	@Test
	void kakaoId_유니크_위반이_나면_재조회해_같은_회원을_반환한다() {
		given(memberKakaoRegistrar.registerOrUpdate(profile))
				.willThrow(new DataIntegrityViolationException("duplicate kakao_id"))
				.willReturn(member);

		assertThat(memberService.findOrCreateByKakaoId(profile)).isEqualTo(member);
		verify(memberKakaoRegistrar, times(2)).registerOrUpdate(profile);
	}

	@Test
	void 재시도도_실패하면_예외를_그대로_던진다() {
		given(memberKakaoRegistrar.registerOrUpdate(profile))
				.willThrow(new DataIntegrityViolationException("duplicate kakao_id"));

		assertThatThrownBy(() -> memberService.findOrCreateByKakaoId(profile))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(memberKakaoRegistrar, times(2)).registerOrUpdate(profile);
	}
}
