package com.poco7250.moyeo.group.domain;

/**
 * 그룹 안 역할. 그룹 관리 권한(G-4, P1)에 대비해 컬럼만 두고, MVP에서는 역할별 로직이 없다.
 */
public enum GroupRole {
	/** 그룹을 만든 사람 */
	OWNER,
	/** 초대 링크로 가입한 사람 */
	MEMBER
}
