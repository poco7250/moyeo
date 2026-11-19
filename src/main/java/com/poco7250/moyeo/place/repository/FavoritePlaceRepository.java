package com.poco7250.moyeo.place.repository;

import com.poco7250.moyeo.place.domain.FavoritePlace;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 그룹 단골 장소 저장소.
 */
public interface FavoritePlaceRepository extends JpaRepository<FavoritePlace, Long> {

	/**
	 * 그룹의 단골 장소를 최근에 저장한 순으로 조회한다.
	 */
	List<FavoritePlace> findAllByGroupIdOrderByIdDesc(Long groupId);

	/**
	 * 저장 전 개수·중복 검사용으로 그룹의 단골 장소를 잠금 읽기(locking read)로 조회한다.
	 * 일반 조회는 트랜잭션 첫 조회 시점 스냅샷을 읽어(MySQL REPEATABLE READ) 그룹 잠금을 기다리는 동안 먼저 커밋된 단골을
	 * 못 볼 수 있으므로, 잠금 읽기로 항상 최신 커밋을 읽는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select f from FavoritePlace f where f.group.id = :groupId")
	List<FavoritePlace> findAllByGroupIdForCheck(@Param("groupId") Long groupId);

	/**
	 * 이 그룹에 속한 단골 장소만 조회한다(다른 그룹 것은 없는 것으로 본다).
	 */
	Optional<FavoritePlace> findByIdAndGroupId(Long id, Long groupId);
}
