package com.poco7250.moyeo.push.repository;

import com.poco7250.moyeo.push.domain.PushSubscription;
import com.poco7250.moyeo.push.domain.PushType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 웹 푸시 구독 저장소.
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	/**
	 * endpoint 해시로 구독을 찾는다(같은 기기 재등록).
	 */
	Optional<PushSubscription> findByEndpointHash(String endpointHash);

	/**
	 * 회원의 구독을 최근 등록 순으로 조회한다(기기 수 상한 정리).
	 */
	List<PushSubscription> findAllByMemberIdOrderByRegisteredAtDescIdDesc(Long memberId);

	/**
	 * 회원 본인의 구독 하나를 지운다. 다른 회원 구독이면 아무것도 지우지 않는다.
	 *
	 * @return 지운 행 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PushSubscription s where s.member.id = :memberId and s.endpointHash = :endpointHash")
	int deleteMine(@Param("memberId") Long memberId, @Param("endpointHash") String endpointHash);

	/**
	 * 받는 회원들의 구독 중 그 종류를 끄지 않은 회원의 구독만 조회한다(전송 대상, 쿼리 1번).
	 */
	@Query("select s from PushSubscription s where s.member.id in :memberIds and not exists ("
			+ "select 1 from PushMute m where m.member.id = s.member.id and m.type = :type)")
	List<PushSubscription> findDeliverable(@Param("memberIds") Collection<Long> memberIds,
			@Param("type") PushType type);
}
