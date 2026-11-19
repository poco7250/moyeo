package com.poco7250.moyeo.group.repository;

import com.poco7250.moyeo.group.domain.Group;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 그룹 저장소.
 */
public interface GroupRepository extends JpaRepository<Group, Long> {

	/**
	 * 그룹 행을 쓰기 잠금으로 읽는다. 그룹 단위 개수·중복 검사(단골 장소)를 동시 요청 사이에서 한 줄로 세울 때 쓴다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Group g where g.id = :groupId")
	Optional<Group> findByIdForUpdate(@Param("groupId") Long groupId);
}
