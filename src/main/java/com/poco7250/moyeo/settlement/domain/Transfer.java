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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 리비전의 송금 안내 1건. 송금은 앱 밖에서 하므로 송금 상태는 두지 않는다(E1).
 */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "revision_id", nullable = false)
	private SettlementRevision revision;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "from_member_id", nullable = false)
	private Member fromMember;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "to_member_id", nullable = false)
	private Member toMember;

	@Column(nullable = false)
	private long amount;

	private Transfer(SettlementRevision revision, Member fromMember, Member toMember, long amount) {
		this.revision = revision;
		this.fromMember = fromMember;
		this.toMember = toMember;
		this.amount = amount;
	}

	/**
	 * 송금 안내 1건을 만든다.
	 */
	public static Transfer create(SettlementRevision revision, Member fromMember, Member toMember, long amount) {
		return new Transfer(revision, fromMember, toMember, amount);
	}
}
