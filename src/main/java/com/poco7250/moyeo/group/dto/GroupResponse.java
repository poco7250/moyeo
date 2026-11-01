package com.poco7250.moyeo.group.dto;

import com.poco7250.moyeo.group.domain.Group;
import java.time.LocalDateTime;

/**
 * 그룹 정보 응답.
 */
public record GroupResponse(Long id, String name, LocalDateTime createdAt) {

	/**
	 * 엔티티를 응답 DTO로 변환한다.
	 */
	public static GroupResponse from(Group group) {
		return new GroupResponse(group.getId(), group.getName(), group.getCreatedAt());
	}
}
