package com.poco7250.moyeo.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.auth.domain.RefreshToken;
import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class RefreshTokenRepositoryTest {

	private static final String HASH_A = "a".repeat(64);
	private static final String HASH_B = "b".repeat(64);
	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 13, 12, 0);

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	MemberRepository memberRepository;

	private Member member;

	@BeforeEach
	void setUp() {
		member = memberRepository.saveAndFlush(Member.ofKakao(1001L, "모여", null, null));
	}

	@Test
	@DisplayName("저장한 토큰을 해시로 찾는다")
	void findByTokenHash() {
		refreshTokenRepository.saveAndFlush(RefreshToken.issue(member, HASH_A, EXPIRES_AT));

		assertThat(refreshTokenRepository.findByTokenHash(HASH_A)).get()
				.satisfies(token -> {
					assertThat(token.getMember().getId()).isEqualTo(member.getId());
					assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
					assertThat(token.getCreatedAt()).isNotNull();
				});
		assertThat(refreshTokenRepository.findByTokenHash(HASH_B)).isEmpty();
	}

	@Test
	@DisplayName("해시로 삭제하면 삭제 건수 1, 없는 해시면 0을 돌려준다")
	void deleteByTokenHash() {
		refreshTokenRepository.saveAndFlush(RefreshToken.issue(member, HASH_A, EXPIRES_AT));

		assertThat(refreshTokenRepository.deleteByTokenHash(HASH_A)).isEqualTo(1);
		assertThat(refreshTokenRepository.deleteByTokenHash(HASH_A)).isZero();
		assertThat(refreshTokenRepository.findByTokenHash(HASH_A)).isEmpty();
	}

	@Test
	@DisplayName("회원의 토큰을 모두 삭제하고 다른 회원 토큰은 남긴다")
	void deleteByMember() {
		Member other = memberRepository.saveAndFlush(Member.ofKakao(1002L, "다른회원", null, null));
		refreshTokenRepository.saveAndFlush(RefreshToken.issue(member, HASH_A, EXPIRES_AT));
		refreshTokenRepository.saveAndFlush(RefreshToken.issue(member, HASH_B, EXPIRES_AT));
		refreshTokenRepository.saveAndFlush(RefreshToken.issue(other, "c".repeat(64), EXPIRES_AT));

		assertThat(refreshTokenRepository.deleteByMember(member)).isEqualTo(2);
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 tokenHash를 두 번 저장하면 유니크 제약 위반이다")
	void duplicateTokenHash() {
		refreshTokenRepository.saveAndFlush(RefreshToken.issue(member, HASH_A, EXPIRES_AT));

		assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(RefreshToken.issue(member, HASH_A, EXPIRES_AT)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
