package com.poco7250.moyeo.group.repository;

import com.poco7250.moyeo.group.domain.InviteLink;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 초대 링크 저장소.
 */
public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {

	/**
	 * 토큰으로 초대 링크를 조회한다.
	 */
	Optional<InviteLink> findByToken(String token);
}
