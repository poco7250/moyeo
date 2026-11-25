package com.poco7250.moyeo.game.repository;

import com.poco7250.moyeo.game.domain.GameParticipant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 게임 참가자 저장소.
 */
public interface GameParticipantRepository extends JpaRepository<GameParticipant, Long> {

	/**
	 * 게임의 참가자를 등록 순으로 조회한다.
	 */
	List<GameParticipant> findAllByGameSessionIdOrderByIdAsc(Long gameSessionId);

	/**
	 * 여러 게임의 참가자를 회원과 함께 id 순으로 조회한다(정산의 벌칙 나눠 받는 사람 계산).
	 */
	@Query("select gp from GameParticipant gp join fetch gp.member "
			+ "where gp.gameSession.id in :gameSessionIds order by gp.id")
	List<GameParticipant> findAllWithMemberByGameSessionIdIn(@Param("gameSessionIds") Collection<Long> gameSessionIds);

	/**
	 * 회원이 한 번이라도 게임 참가자였는지 본다(게스트 삭제 가능 검사).
	 */
	boolean existsByMemberId(Long memberId);
}
