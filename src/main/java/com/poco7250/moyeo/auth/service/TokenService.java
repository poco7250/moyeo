package com.poco7250.moyeo.auth.service;

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
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 자체 토큰(Access·Refresh) 발급·재발급(회전)·폐기.
 * 토큰 원문과 해시는 로그에 남기지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final MemberRepository memberRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	/**
	 * 회원에게 새 Access·Refresh 토큰을 발급하고, Refresh는 해시로 저장한다.
	 */
	public TokenPair issue(Long memberId) {
		Member member = memberRepository.getReferenceById(memberId);
		String rawRefreshToken = refreshTokenGenerator.generate();
		LocalDateTime expiresAt = LocalDateTime.now(clock).plus(jwtProperties.refreshTokenTtl());
		refreshTokenRepository.save(
				RefreshToken.issue(member, refreshTokenGenerator.hash(rawRefreshToken), expiresAt));
		return new TokenPair(jwtTokenProvider.createAccessToken(memberId), rawRefreshToken);
	}

	/**
	 * Refresh 토큰으로 새 토큰 쌍을 발급하고, 쓴 Refresh는 지운다(회전).
	 * 이미 회전된 토큰을 다시 쓰거나 동시에 두 번 요청하면 하나만 성공한다.
	 *
	 * @throws BusinessException 없거나 만료됐거나 이미 회전된 토큰이면 INVALID_REFRESH_TOKEN
	 */
	public TokenPair reissue(String refreshToken) {
		if (!StringUtils.hasText(refreshToken)) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		String tokenHash = refreshTokenGenerator.hash(refreshToken);
		RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		if (stored.isExpired(LocalDateTime.now(clock))) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		// 조회와 삭제 사이에 다른 요청이 먼저 회전했으면 삭제 건수가 0이다
		if (refreshTokenRepository.deleteByTokenHash(tokenHash) == 0) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		return issue(stored.getMember().getId());
	}

	/**
	 * Refresh 토큰을 폐기한다(로그아웃). 없거나 이미 지워진 토큰이어도 조용히 넘어간다.
	 */
	public void revoke(String refreshToken) {
		if (!StringUtils.hasText(refreshToken)) {
			return;
		}
		refreshTokenRepository.deleteByTokenHash(refreshTokenGenerator.hash(refreshToken));
	}
}
