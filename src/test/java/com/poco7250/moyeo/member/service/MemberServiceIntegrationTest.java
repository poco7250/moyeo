package com.poco7250.moyeo.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.poco7250.moyeo.member.dto.KakaoProfile;
import com.poco7250.moyeo.member.dto.MemberResponse;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MemberServiceIntegrationTest {

	@Autowired
	MemberService memberService;

	@Autowired
	MemberRepository memberRepository;

	@AfterEach
	void tearDown() {
		// registrar가 REQUIRES_NEW로 커밋하므로 테스트 롤백이 안 됨 → 직접 정리
		memberRepository.deleteAllInBatch();
	}

	@Test
	void 같은_kakaoId로_다시_호출하면_추가_생성_없이_같은_회원을_반환한다() {
		MemberResponse first = memberService.findOrCreateByKakaoId(new KakaoProfile(1001L, "모여", null, null));
		MemberResponse second = memberService.findOrCreateByKakaoId(
				new KakaoProfile(1001L, "새닉네임", "https://img.kakao.com/b.png", null));

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(second.nickname()).isEqualTo("새닉네임");
		assertThat(memberRepository.count()).isEqualTo(1);
	}
}
