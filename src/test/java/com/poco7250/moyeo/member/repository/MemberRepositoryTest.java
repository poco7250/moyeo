package com.poco7250.moyeo.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class MemberRepositoryTest {

	@Autowired
	MemberRepository memberRepository;

	@Test
	void 같은_kakaoId로_두_번_저장하면_유니크_제약_위반이다() {
		memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, null));

		assertThatThrownBy(() -> memberRepository.saveAndFlush(Member.ofKakao(1001L, "다른닉네임", null, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 이메일_없이_저장할_수_있다() {
		Member saved = memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, null));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getEmail()).isNull();
	}

	@Test
	void 같은_닉네임의_회원_두_명을_저장할_수_있다() {
		memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, null));
		memberRepository.saveAndFlush(Member.ofKakao(1002L, "모여", null, null));

		assertThat(memberRepository.count()).isEqualTo(2);
	}

	@Test
	void kakaoId로_회원을_찾는다() {
		Member saved = memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, "user@kakao.com"));

		assertThat(memberRepository.findByKakaoId(1001L)).get().extracting(Member::getId).isEqualTo(saved.getId());
		assertThat(memberRepository.findByKakaoId(9999L)).isEmpty();
	}
}
