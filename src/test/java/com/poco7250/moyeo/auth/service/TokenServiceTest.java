package com.poco7250.moyeo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.auth.config.JwtProperties;
import com.poco7250.moyeo.auth.domain.RefreshToken;
import com.poco7250.moyeo.auth.dto.TokenPair;
import com.poco7250.moyeo.auth.repository.RefreshTokenRepository;
import com.poco7250.moyeo.auth.token.JwtTokenProvider;
import com.poco7250.moyeo.auth.token.RefreshTokenGenerator;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.support.TestClocks;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TokenService 단위 테스트. 저장소는 목, JWT·해시·Clock은 실제(고정 Clock) 객체를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);
	private static final Duration REFRESH_TTL = Duration.ofDays(14);
	private static final Long MEMBER_ID = 1L;

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@Mock
	MemberRepository memberRepository;

	private final RefreshTokenGenerator generator = new RefreshTokenGenerator();
	private final Clock clock = TestClocks.fixed(NOW);
	private final JwtProperties jwtProperties = new JwtProperties(
			"test-jwt-secret-key-for-moyeo-must-be-32-bytes-or-more", Duration.ofMinutes(30), REFRESH_TTL);
	private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties, clock);

	private TokenService tokenService;
	private Member member;

	@BeforeEach
	void setUp() {
		tokenService = new TokenService(refreshTokenRepository, memberRepository, jwtTokenProvider,
				generator, jwtProperties, clock);
		member = Member.ofKakao(1001L, "모여", null, null);
		ReflectionTestUtils.setField(member, "id", MEMBER_ID);
	}

	@Test
	@DisplayName("발급하면 Access는 memberId를 담고, Refresh는 원문이 아닌 해시와 14일 뒤 만료로 저장된다")
	void issue() {
		given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);

		TokenPair pair = tokenService.issue(MEMBER_ID);

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		RefreshToken saved = captor.getValue();
		assertThat(jwtTokenProvider.parseMemberId(pair.accessToken())).isEqualTo(MEMBER_ID);
		assertThat(saved.getTokenHash()).isEqualTo(generator.hash(pair.refreshToken())).isNotEqualTo(pair.refreshToken());
		assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
		assertThat(saved.getMember()).isSameAs(member);
	}

	@Test
	@DisplayName("재발급하면 새 토큰 쌍이 나오고, 이전 토큰으로 다시 요청하면 INVALID_REFRESH_TOKEN")
	void reissueRotates() {
		String oldToken = generator.generate();
		String oldHash = generator.hash(oldToken);
		given(refreshTokenRepository.findByTokenHash(oldHash))
				.willReturn(Optional.of(RefreshToken.issue(member, oldHash, NOW.plus(REFRESH_TTL))))
				.willReturn(Optional.empty());
		given(refreshTokenRepository.deleteByTokenHash(oldHash)).willReturn(1);
		given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);

		TokenPair pair = tokenService.reissue(oldToken);

		assertThat(pair.refreshToken()).isNotEqualTo(oldToken);
		assertThat(jwtTokenProvider.parseMemberId(pair.accessToken())).isEqualTo(MEMBER_ID);
		verify(refreshTokenRepository).deleteByTokenHash(oldHash);
		assertErrorCode(() -> tokenService.reissue(oldToken));
	}

	@Test
	@DisplayName("만료 시각이 지난 Refresh면 INVALID_REFRESH_TOKEN이고 삭제·발급하지 않는다")
	void reissueExpired() {
		String token = generator.generate();
		String hash = generator.hash(token);
		given(refreshTokenRepository.findByTokenHash(hash))
				.willReturn(Optional.of(RefreshToken.issue(member, hash, NOW)));

		assertErrorCode(() -> tokenService.reissue(token));
		verify(refreshTokenRepository, never()).deleteByTokenHash(anyString());
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	@DisplayName("조회 뒤 삭제 건수가 0이면(동시 회전) INVALID_REFRESH_TOKEN이고 새로 발급하지 않는다")
	void reissueConcurrentRotation() {
		String token = generator.generate();
		String hash = generator.hash(token);
		given(refreshTokenRepository.findByTokenHash(hash))
				.willReturn(Optional.of(RefreshToken.issue(member, hash, NOW.plus(REFRESH_TTL))));
		given(refreshTokenRepository.deleteByTokenHash(hash)).willReturn(0);

		assertErrorCode(() -> tokenService.reissue(token));
		verify(refreshTokenRepository, never()).save(any());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "unknown-token"})
	@DisplayName("비어 있거나 저장되지 않은 Refresh면 INVALID_REFRESH_TOKEN")
	void reissueUnknown(String token) {
		assertErrorCode(() -> tokenService.reissue(token));
	}

	@Test
	@DisplayName("폐기하면 해시로 삭제하고, 없는 토큰이어도 예외 없이 넘어간다")
	void revoke() {
		String token = generator.generate();
		given(refreshTokenRepository.deleteByTokenHash(generator.hash(token))).willReturn(0);

		tokenService.revoke(token);

		verify(refreshTokenRepository).deleteByTokenHash(generator.hash(token));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@DisplayName("빈 값을 폐기하면 저장소를 건드리지 않는다")
	void revokeBlank(String token) {
		tokenService.revoke(token);

		verify(refreshTokenRepository, never()).deleteByTokenHash(anyString());
	}

	private void assertErrorCode(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
	}
}
