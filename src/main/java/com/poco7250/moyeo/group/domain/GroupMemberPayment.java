package com.poco7250.moyeo.group.domain;

import com.poco7250.moyeo.global.common.BaseTimeEntity;
import com.poco7250.moyeo.global.validation.HttpsUrl;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 멤버가 정산 송금을 받을 계좌·송금 링크(PRD G-5·S-7, 2026-11-15 결정). 그룹 멤버당 0..1건.
 * 계좌번호는 서비스가 AES-GCM으로 암호화한 값만 들고 있다(평문은 엔티티에 두지 않는다).
 * GroupMember 컬럼으로 두지 않은 이유: 프로필 변경이 전체 행 UPDATE라 겹치면 서로 덮어쓰고,
 * 멤버 목록을 읽을 때마다 암호문까지 읽게 되기 때문이다.
 */
@Getter
@Entity
@Table(
		name = "group_member_payment",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_group_member_payment_group_member_id", columnNames = "group_member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMemberPayment extends BaseTimeEntity {

	public static final int BANK_NAME_MAX_LENGTH = 20;
	public static final int ACCOUNT_HOLDER_MAX_LENGTH = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_member_id", nullable = false)
	private GroupMember groupMember;

	@Column(length = BANK_NAME_MAX_LENGTH)
	private String bankName;

	// Base64(IV ‖ 암호문 + 태그). 평문 30자 기준 80자 안팎이라 여유를 둔다
	@Column(length = 128)
	private String encryptedAccountNumber;

	@Column(length = ACCOUNT_HOLDER_MAX_LENGTH)
	private String accountHolder;

	@Column(length = HttpsUrl.MAX_LENGTH)
	private String tossLink;

	@Column(length = HttpsUrl.MAX_LENGTH)
	private String kakaoPayLink;

	@Version
	private Long version;

	private GroupMemberPayment(GroupMember groupMember) {
		this.groupMember = groupMember;
	}

	/**
	 * 그룹 멤버의 송금 정보를 만든다.
	 *
	 * @throws IllegalArgumentException 계좌 3항목이 일부만 있거나, 계좌·링크가 하나도 없으면
	 */
	public static GroupMemberPayment create(GroupMember groupMember, String bankName, String encryptedAccountNumber,
			String accountHolder, String tossLink, String kakaoPayLink) {
		GroupMemberPayment payment = new GroupMemberPayment(groupMember);
		payment.replace(bankName, encryptedAccountNumber, accountHolder, tossLink, kakaoPayLink);
		return payment;
	}

	/**
	 * 송금 정보를 통째로 바꾼다.
	 *
	 * @throws IllegalArgumentException 계좌 3항목이 일부만 있거나, 계좌·링크가 하나도 없으면
	 */
	public void replace(String bankName, String encryptedAccountNumber, String accountHolder, String tossLink,
			String kakaoPayLink) {
		boolean anyAccount = bankName != null || encryptedAccountNumber != null || accountHolder != null;
		boolean fullAccount = bankName != null && encryptedAccountNumber != null && accountHolder != null;
		if (anyAccount && !fullAccount) {
			throw new IllegalArgumentException("계좌는 은행·계좌번호·예금주를 모두 입력해야 합니다.");
		}
		if (!fullAccount && tossLink == null && kakaoPayLink == null) {
			throw new IllegalArgumentException("계좌나 송금 링크 중 하나는 있어야 합니다.");
		}
		this.bankName = bankName;
		this.encryptedAccountNumber = encryptedAccountNumber;
		this.accountHolder = accountHolder;
		this.tossLink = tossLink;
		this.kakaoPayLink = kakaoPayLink;
	}
}
