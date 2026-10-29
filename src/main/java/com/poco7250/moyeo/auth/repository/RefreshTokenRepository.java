package com.poco7250.moyeo.auth.repository;

import com.poco7250.moyeo.auth.domain.RefreshToken;
import com.poco7250.moyeo.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Refresh 토큰 저장소.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * 해시로 토큰을 바로 삭제하고 삭제 건수를 돌려준다.
	 * 파생 deleteBy는 조회 후 삭제라 동시 회전을 못 잡으므로, 벌크 DELETE의 영향 행 수(0이면 이미 회전됨)로 판단한다.
	 */
	@Modifying
	@Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
	int deleteByTokenHash(@Param("tokenHash") String tokenHash);

	/**
	 * 회원의 Refresh 토큰을 모두 삭제한다(전체 로그아웃·탈퇴용).
	 */
	@Modifying
	@Query("delete from RefreshToken r where r.member = :member")
	int deleteByMember(@Param("member") Member member);
}
