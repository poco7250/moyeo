package com.poco7250.moyeo.push.repository;

import com.poco7250.moyeo.push.domain.PushMute;
import com.poco7250.moyeo.push.domain.PushType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 꺼 둔 푸시 종류 저장소.
 */
public interface PushMuteRepository extends JpaRepository<PushMute, Long> {

	/**
	 * 회원이 꺼 둔 종류를 모두 조회한다(설정 조회).
	 */
	List<PushMute> findAllByMemberId(Long memberId);

	/**
	 * 회원의 한 종류를 다시 켠다(끈 기록 삭제).
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PushMute m where m.member.id = :memberId and m.type = :type")
	int deleteByMemberIdAndType(@Param("memberId") Long memberId, @Param("type") PushType type);
}
