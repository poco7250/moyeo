package com.poco7250.moyeo.group.repository;

import com.poco7250.moyeo.group.domain.InviteLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 초대 링크 저장소.
 */
public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {

	/**
	 * 토큰으로 초대 링크를 조회한다.
	 */
	Optional<InviteLink> findByToken(String token);

	/**
	 * 토큰으로 초대 링크를 그룹과 함께 조회한다(초대 미리보기·가입, 추가 쿼리 방지).
	 */
	@Query("select il from InviteLink il join fetch il.group where il.token = :token")
	Optional<InviteLink> findWithGroupByToken(@Param("token") String token);

	/**
	 * 그룹에서 아직 폐기되지 않은 링크를 모두 조회한다(재발급 시 폐기 대상, 만료 여부는 따지지 않음).
	 */
	List<InviteLink> findAllByGroupIdAndRevokedAtIsNull(Long groupId);
}
