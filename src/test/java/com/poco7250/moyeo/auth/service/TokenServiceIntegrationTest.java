package com.poco7250.moyeo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.auth.domain.RefreshToken;
import com.poco7250.moyeo.auth.dto.TokenPair;
import com.poco7250.moyeo.auth.repository.RefreshTokenRepository;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenServiceIntegrationTest {

	@Autowired
	TokenService tokenService;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	MemberRepository memberRepository;

	@AfterEach
	void tearDown() {
		// TokenService가 트랜잭션을 커밋하므로 직접 정리(FK 때문에 토큰 먼저)
		refreshTokenRepository.deleteAllInBatch();
		memberRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("발급 → 재발급하면 새 쌍이 나오고 이전 Refresh는 더 이상 쓸 수 없다")
	void issueReissueRejectsOldToken() {
		Member member = memberRepository.save(Member.ofKakao(1001L, "모여", null, null));
		TokenPair first = tokenService.issue(member.getId());

		TokenPair second = tokenService.reissue(first.refreshToken());

		assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
		assertThatThrownBy(() -> tokenService.reissue(first.refreshToken()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
		assertThat(refreshTokenRepository.findAll()).singleElement()
				.extracting(RefreshToken::getTokenHash)
				.satisfies(hash -> assertThat(hash).hasSize(64).isNotEqualTo(second.refreshToken()));
	}

	@Test
	@DisplayName("폐기한 Refresh로는 재발급할 수 없다")
	void revokedTokenCannotReissue() {
		Member member = memberRepository.save(Member.ofKakao(1001L, "모여", null, null));
		TokenPair pair = tokenService.issue(member.getId());

		tokenService.revoke(pair.refreshToken());

		assertThat(refreshTokenRepository.count()).isZero();
		assertThatThrownBy(() -> tokenService.reissue(pair.refreshToken()))
				.isInstanceOf(BusinessException.class);
	}
}
