package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyMoneyType;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.PenaltyCharge;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.domain.Transfer;
import com.poco7250.moyeo.settlement.dto.NetBalanceChangeResponse;
import com.poco7250.moyeo.settlement.dto.RevisionPenaltyChargeResponse;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementRevisionResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.repository.PenaltyChargeRepository;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import com.poco7250.moyeo.settlement.repository.TransferRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementHistoryServiceTest {

	private static final Long MEETING_ID = 2L;
	private static final Long MEMBER_ID = 1L;
	private static final Long SETTLEMENT_ID = 50L;
	private static final LocalDateTime AT = LocalDateTime.of(2026, 12, 20, 22, 0);

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	SettlementRepository settlementRepository;

	@Mock
	SettlementRevisionRepository revisionRepository;

	@Mock
	RevisionMemberShareRepository memberShareRepository;

	@Mock
	TransferRepository transferRepository;

	@Mock
	PenaltyChargeRepository penaltyChargeRepository;

	@InjectMocks
	SettlementHistoryService service;

	private Settlement settlement;

	@BeforeEach
	void setUp() {
		settlement = Settlement.createDraft(null);
		ReflectionTestUtils.setField(settlement, "id", SETTLEMENT_ID);
	}

	@Test
	@DisplayName("리비전 3개를 번호 순으로 주고, 변동액은 인접 리비전끼리 파생하며 리비전 1은 null이다")
	void revisionsWithAdjacentDeltas() {
		SettlementRevision first = revision(500L, 1, false);
		SettlementRevision second = revision(501L, 2, false);
		SettlementRevision third = revision(502L, 3, true);
		givenRevisions(List.of(first, second, third));
		given(memberShareRepository.findAllByRevisionIdInOrderByMemberIdAsc(List.of(500L, 501L, 502L)))
				.willReturn(List.of(
						share(first, 1L, 10_000L, 20_000L), share(first, 2L, 10_000L, -10_000L),
						share(first, 3L, 10_000L, -10_000L),
						share(second, 1L, 15_000L, 15_000L), share(second, 2L, 15_000L, -15_000L),
						share(third, 1L, 15_000L, 15_000L), share(third, 2L, 15_000L, -15_000L)));
		given(transferRepository.findAllByRevisionIdInOrderByIdAsc(List.of(500L, 501L, 502L))).willReturn(List.of(
				transfer(first, 2L, 1L, 10_000L), transfer(first, 3L, 1L, 10_000L),
				transfer(second, 2L, 1L, 15_000L)));
		given(penaltyChargeRepository.findAllWithPenaltyByRevisionIdIn(List.of(500L, 501L, 502L)))
				.willReturn(List.of());

		List<SettlementRevisionResponse> responses = service.revisions(MEETING_ID, MEMBER_ID);

		assertThat(responses).extracting(SettlementRevisionResponse::revisionNo).containsExactly(1, 2, 3);
		assertThat(responses.get(0).confirmedBy()).isEqualTo(MEMBER_ID);
		assertThat(responses.get(0).confirmedAt()).isEqualTo(AT.plusHours(1));
		assertThat(responses.get(0).changes()).isNull();
		assertThat(responses.get(0).members()).containsExactly(new SettlementMemberResponse(1L, 10_000L, 20_000L),
				new SettlementMemberResponse(2L, 10_000L, -10_000L), new SettlementMemberResponse(3L, 10_000L, -10_000L));
		assertThat(responses.get(0).transfers()).containsExactly(new SettlementTransferResponse(2L, 1L, 10_000L),
				new SettlementTransferResponse(3L, 1L, 10_000L));
		assertThat(responses.get(1).changes()).containsExactly(
				new NetBalanceChangeResponse(1L, 20_000L, 15_000L, -5_000L),
				new NetBalanceChangeResponse(2L, -10_000L, -15_000L, -5_000L),
				new NetBalanceChangeResponse(3L, -10_000L, 0L, 10_000L));
		assertThat(responses.get(2).changes()).extracting(NetBalanceChangeResponse::delta).containsExactly(0L, 0L);
		assertThat(responses.get(2).warningsAcknowledged()).isTrue();
		assertThat(responses.get(2).transfers()).isEmpty();
		assertThat(responses.get(2).penaltyCharges()).isEmpty();
	}

	@Test
	@DisplayName("리비전에 고정된 벌칙 부담액과 조건 스냅샷을 벌칙자 id와 함께 준다")
	void penaltyChargeSnapshot() {
		SettlementRevision first = revision(500L, 1, false);
		givenRevisions(List.of(first));
		Round round = PenaltyFixtures.round(5L, 2, "볼링장");
		Expense bowling = PenaltyFixtures.expense(31L, round, 1L, 100_000L, "볼링");
		GameSession game = PenaltyFixtures.revealedMoneyGame(70L, round, AT);
		Penalty penalty = PenaltyFixtures.penalty(100L, game, 2L);
		PenaltyCharge charge = PenaltyCharge.create(first, penalty, 30_000L,
				PenaltyFixtures.ratioRule(90L, game, 30, bowling, MEMBER_ID, AT));
		given(memberShareRepository.findAllByRevisionIdInOrderByMemberIdAsc(List.of(500L))).willReturn(List.of());
		given(transferRepository.findAllByRevisionIdInOrderByIdAsc(List.of(500L))).willReturn(List.of());
		given(penaltyChargeRepository.findAllWithPenaltyByRevisionIdIn(List.of(500L))).willReturn(List.of(charge));

		List<SettlementRevisionResponse> responses = service.revisions(MEETING_ID, MEMBER_ID);

		assertThat(responses.getFirst().penaltyCharges()).containsExactly(new RevisionPenaltyChargeResponse(100L, 2L,
				30_000L, PenaltyMoneyType.RATIO, null, 30, 31L, 100_000L, "볼링"));
	}

	@Test
	@DisplayName("확정 전이면 빈 목록이고 하위 스냅샷을 읽지 않는다")
	void noRevisions() {
		givenRevisions(List.of());

		assertThat(service.revisions(MEETING_ID, MEMBER_ID)).isEmpty();

		verifyNoInteractions(memberShareRepository, transferRepository, penaltyChargeRepository);
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403 NOT_GROUP_MEMBER이고 정산을 읽지 않는다")
	void rejectsNonMember() {
		given(meetingAccessChecker.requireMember(MEETING_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertThatThrownBy(() -> service.revisions(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(settlementRepository, revisionRepository);
	}

	@Test
	@DisplayName("정산이 없으면 404 MEETING_NOT_FOUND")
	void settlementNotFound() {
		given(settlementRepository.findByMeetingId(MEETING_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.revisions(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETING_NOT_FOUND);
		verifyNoInteractions(revisionRepository);
	}

	private void givenRevisions(List<SettlementRevision> revisions) {
		given(settlementRepository.findByMeetingId(MEETING_ID)).willReturn(Optional.of(settlement));
		given(revisionRepository.findAllBySettlementIdOrderByRevisionNoAsc(SETTLEMENT_ID)).willReturn(revisions);
	}

	private SettlementRevision revision(Long id, int revisionNo, boolean acknowledged) {
		SettlementRevision revision = SettlementRevision.create(settlement, revisionNo,
				PenaltyFixtures.member(MEMBER_ID), AT.plusHours(revisionNo), acknowledged);
		ReflectionTestUtils.setField(revision, "id", id);
		return revision;
	}

	private RevisionMemberShare share(SettlementRevision revision, Long memberId, long share, long net) {
		return RevisionMemberShare.create(revision, PenaltyFixtures.member(memberId), share, net);
	}

	private Transfer transfer(SettlementRevision revision, Long from, Long to, long amount) {
		return Transfer.create(revision, PenaltyFixtures.member(from), PenaltyFixtures.member(to), amount);
	}
}
