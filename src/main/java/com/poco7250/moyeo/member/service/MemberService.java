package com.poco7250.moyeo.member.service;

import com.poco7250.moyeo.member.dto.KakaoProfile;
import com.poco7250.moyeo.member.dto.MemberResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 회원 관련 비즈니스 로직.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberKakaoRegistrar memberKakaoRegistrar;

	/**
	 * kakaoId로 회원을 찾아 프로필을 갱신하고, 없으면 새로 만든다.
	 * 같은 사용자의 동시 최초 로그인으로 kakaoId 유니크 위반이 나면, 먼저 생성된 회원을 다시 조회해 돌려준다.
	 * 트랜잭션은 registrar가 호출마다 따로 연다(실패한 트랜잭션 안에서는 재조회할 수 없으므로).
	 */
	public MemberResponse findOrCreateByKakaoId(KakaoProfile profile) {
		try {
			return memberKakaoRegistrar.registerOrUpdate(profile);
		} catch (DataIntegrityViolationException e) {
			log.info("카카오 회원 동시 생성 감지, 재조회: {}", e.getMostSpecificCause().getClass().getSimpleName());
			return memberKakaoRegistrar.registerOrUpdate(profile);
		}
	}
}
