package com.poco7250.moyeo.settlement.domain;

import com.poco7250.moyeo.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 리비전의 회원별 부담액·순잔액 스냅샷.
 */
@Getter
@Entity
@Immutable
@Table(
		uniqueConstraints = @UniqueConstraint(
				name = "uk_revision_member_share_revision_id_member_id",
				columnNames = {"revision_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionMemberShare {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "revision_id", nullable = false)
	private SettlementRevision revision;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false)
	private long shareAmount;

	// 받을 돈은 +, 낼 돈은 −
	@Column(nullable = false)
	private long netBalance;

	private RevisionMemberShare(SettlementRevision revision, Member member, long shareAmount, long netBalance) {
		this.revision = revision;
		this.member = member;
		this.shareAmount = shareAmount;
		this.netBalance = netBalance;
	}

	/**
	 * 회원 한 명의 스냅샷을 만든다.
	 */
	public static RevisionMemberShare create(SettlementRevision revision, Member member, long shareAmount,
			long netBalance) {
		return new RevisionMemberShare(revision, member, shareAmount, netBalance);
	}
}
