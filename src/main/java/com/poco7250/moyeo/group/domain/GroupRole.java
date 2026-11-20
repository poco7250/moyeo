package com.poco7250.moyeo.group.domain;

/**
 * 그룹 안 역할(G-4). 방장은 그룹당 1명이고 모든 모임에서 호스트 권한을, 총무는 정산 권한을 갖는다.
 */
public enum GroupRole {
	/** 방장. 그룹을 만든 사람이 처음 맡고 다른 멤버에게 넘길 수 있다 */
	OWNER,
	/** 총무. 방장이 지정하며 여러 명일 수 있다. 모든 모임의 정산 확정·재확정과 벌칙 조건을 다룬다 */
	TREASURER,
	/** 일반 멤버 */
	MEMBER;

	/**
	 * 그룹의 모든 모임에서 호스트 권한을 갖는 역할인지 확인한다.
	 */
	public boolean hasHostAuthority() {
		return this == OWNER;
	}

	/**
	 * 그룹의 모든 모임에서 정산 권한(확정·재확정, 벌칙 조건)을 갖는 역할인지 확인한다.
	 */
	public boolean hasSettlementAuthority() {
		return this == OWNER || this == TREASURER;
	}
}
