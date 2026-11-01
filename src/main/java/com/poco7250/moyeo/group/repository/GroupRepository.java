package com.poco7250.moyeo.group.repository;

import com.poco7250.moyeo.group.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 그룹 저장소.
 */
public interface GroupRepository extends JpaRepository<Group, Long> {
}
