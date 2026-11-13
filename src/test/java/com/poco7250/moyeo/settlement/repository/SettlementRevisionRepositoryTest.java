package com.poco7250.moyeo.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.config.JpaAuditingConfig;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.domain.Transfer;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class SettlementRevisionRepositoryTest {

	private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 12, 20, 22, 0);

	@Autowired
	SettlementRevisionRepository revisionRepository;

	@Autowired
	RevisionMemberShareRepository memberShareRepository;

	@Autowired
	TransferRepository transferRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MemberRepository memberRepository;

	private Settlement settlement;
	private Member first;
	private Member second;

	@BeforeEach
	void setUp() {
		Group group = groupRepository.saveAndFlush(Group.create("대학 동기"));
		Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2026, 12, 20, 18, 0), null, null, 1L));
		settlement = settlementRepository.saveAndFlush(Settlement.createDraft(meeting));
		first = memberRepository.saveAndFlush(Member.ofKakao(1001L, "철수", null, null));
		second = memberRepository.saveAndFlush(Member.ofKakao(1002L, "지수", null, null));
	}

	@Test
	@DisplayName("같은 정산에 같은 번호 리비전을 두 번 저장하면 유니크 제약 위반이다")
	void duplicateRevisionNo() {
		saveRevision(1);

		assertThatThrownBy(() -> saveRevision(1)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("정산과 번호로 리비전을 찾는다")
	void findByRevisionNo() {
		SettlementRevision saved = saveRevision(1);
		saveRevision(2);

		assertThat(revisionRepository.findBySettlementIdAndRevisionNo(settlement.getId(), 1))
				.hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(saved.getId()));
		assertThat(revisionRepository.findBySettlementIdAndRevisionNo(settlement.getId(), 3)).isEmpty();
	}

	@Test
	@DisplayName("같은 리비전에 같은 회원 스냅샷을 두 번 저장하면 유니크 제약 위반이다")
	void duplicateMemberShare() {
		SettlementRevision revision = saveRevision(1);
		memberShareRepository.saveAndFlush(RevisionMemberShare.create(revision, first, 10_000L, 20_000L));

		assertThatThrownBy(() -> memberShareRepository.saveAndFlush(
				RevisionMemberShare.create(revision, first, 5_000L, 0L)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("회원 스냅샷은 회원 id 순으로, 다른 리비전을 빼고 조회한다")
	void memberSharesOrdered() {
		SettlementRevision revision = saveRevision(1);
		SettlementRevision otherRevision = saveRevision(2);
		memberShareRepository.saveAndFlush(RevisionMemberShare.create(revision, second, 10_000L, -10_000L));
		memberShareRepository.saveAndFlush(RevisionMemberShare.create(revision, first, 10_000L, 10_000L));
		memberShareRepository.saveAndFlush(RevisionMemberShare.create(otherRevision, first, 1L, 1L));

		assertThat(memberShareRepository.findAllByRevisionIdOrderByMemberIdAsc(revision.getId()))
				.extracting(share -> share.getMember().getId())
				.containsExactly(first.getId(), second.getId());
	}

	@Test
	@DisplayName("송금 안내는 저장한 순서대로, 다른 리비전을 빼고 조회한다")
	void transfersOrdered() {
		SettlementRevision revision = saveRevision(1);
		SettlementRevision otherRevision = saveRevision(2);
		transferRepository.saveAndFlush(Transfer.create(revision, second, first, 30_000L));
		transferRepository.saveAndFlush(Transfer.create(revision, first, second, 10_000L));
		transferRepository.saveAndFlush(Transfer.create(otherRevision, first, second, 1L));

		assertThat(transferRepository.findAllByRevisionIdOrderByIdAsc(revision.getId()))
				.extracting(Transfer::getAmount)
				.containsExactly(30_000L, 10_000L);
	}

	@Test
	@DisplayName("정산의 모든 리비전을 번호 순으로 조회한다")
	void findAllOrdered() {
		saveRevision(2);
		saveRevision(1);

		assertThat(revisionRepository.findAllBySettlementIdOrderByRevisionNoAsc(settlement.getId()))
				.extracting(SettlementRevision::getRevisionNo)
				.containsExactly(1, 2);
	}

	@Test
	@DisplayName("여러 리비전의 회원 스냅샷·송금 안내를 한 번에 조회하고 목록에 없는 리비전은 뺀다")
	void findManyRevisions() {
		SettlementRevision revision1 = saveRevision(1);
		SettlementRevision revision2 = saveRevision(2);
		SettlementRevision excluded = saveRevision(3);
		memberShareRepository.saveAndFlush(RevisionMemberShare.create(revision2, second, 1L, -1L));
		memberShareRepository.saveAndFlush(RevisionMemberShare.create(revision1, first, 2L, 2L));
		memberShareRepository.saveAndFlush(RevisionMemberShare.create(excluded, first, 3L, 3L));
		transferRepository.saveAndFlush(Transfer.create(revision2, second, first, 20_000L));
		transferRepository.saveAndFlush(Transfer.create(revision1, second, first, 10_000L));
		transferRepository.saveAndFlush(Transfer.create(excluded, second, first, 1L));
		List<Long> revisionIds = List.of(revision1.getId(), revision2.getId());

		assertThat(memberShareRepository.findAllByRevisionIdInOrderByMemberIdAsc(revisionIds))
				.extracting(RevisionMemberShare::getShareAmount)
				.containsExactly(2L, 1L);
		assertThat(transferRepository.findAllByRevisionIdInOrderByIdAsc(revisionIds))
				.extracting(Transfer::getAmount)
				.containsExactly(20_000L, 10_000L);
	}

	private SettlementRevision saveRevision(int revisionNo) {
		return revisionRepository.saveAndFlush(
				SettlementRevision.create(settlement, revisionNo, first, CONFIRMED_AT, false));
	}
}
