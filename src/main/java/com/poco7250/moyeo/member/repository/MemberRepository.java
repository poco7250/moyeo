package com.poco7250.moyeo.member.repository;

import com.poco7250.moyeo.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 저장소.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);
}
