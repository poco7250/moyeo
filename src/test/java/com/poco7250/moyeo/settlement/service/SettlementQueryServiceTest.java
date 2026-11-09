package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.settlement.calc.ExpenseInput;
import com.poco7250.moyeo.settlement.calc.SettlementInput;
import com.poco7250.moyeo.settlement.calc.SettlementWarningType;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.domain.Transfer;
import com.poco7250.moyeo.settlement.dto.SettlementMemberResponse;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.dto.SettlementTransferResponse;
import com.poco7250.moyeo.settlement.dto.SettlementWarningResponse;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import com.poco7250.moyeo.settlement.repository.TransferRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

	private static final Long MEETING_ID = 2L;
	private static final Long MEMBER_ID = 1L;
	private static final Long SETTLEMENT_ID = 40L;
	private static final Long REVISION_ID = 50L;

	@Mock
	MeetingAccessChecker meetingAccessChecker;

	@Mock
	SettlementRepository settlementRepository;

	@Mock
	SettlementInputAssembler inputAssembler;

	@Mock
	SettlementRevisionRepository revisionRepository;

	@Mock
	RevisionMemberShareRepository memberShareRepository;

	@Mock
	TransferRepository transferRepository;

	@InjectMocks
	SettlementQueryService service;

	private Settlement settlement;

	@BeforeEach
	void setUp() {
		settlement = Settlement.createDraft(null);
		ReflectionTestUtils.setField(settlement, "id", SETTLEMENT_ID);
	}

	@Test
	@DisplayName("DRAFT면 현재 계산값을 기준 리비전 없는 미리보기로 돌려주고 리비전을 읽지 않는다")
	void draftPreview() {
		givenSettlement();
		given(inputAssembler.assemble(MEETING_ID)).willReturn(new SettlementInput(List.of(
				new ExpenseInput(31L, 20_000L, 1L, Set.of(1L, 2L), List.of()))));

		SettlementResponse response = service.get(MEETING_ID, MEMBER_ID);

		assertThat(response).isEqualTo(new SettlementResponse(SettlementStatus.DRAFT, null, true,
				List.of(new SettlementMemberResponse(1L, 10_000L, 10_000L),
						new SettlementMemberResponse(2L, 10_000L, -10_000L)),
				List.of(new SettlementTransferResponse(2L, 1L, 10_000L)),
				List.of()));
		verifyNoInteractions(revisionRepository, memberShareRepository, transferRepository);
	}

	@Test
	@DisplayName("DRAFT 미리보기에는 계산 경고가 담긴다")
	void draftWarnings() {
		givenSettlement();
		given(inputAssembler.assemble(MEETING_ID)).willReturn(new SettlementInput(List.of(
				new ExpenseInput(31L, 20_000L, 1L, Set.of(), List.of()))));

		assertThat(service.get(MEETING_ID, MEMBER_ID).warnings())
				.containsExactly(new SettlementWarningResponse(SettlementWarningType.NO_SHARE_TARGET, 31L));
	}

	@ParameterizedTest
	@EnumSource(value = SettlementStatus.class, names = {"CONFIRMED", "REOPENED"})
	@DisplayName("확정 이후에는 현재 계산 없이 마지막 확정 리비전의 저장값을 돌려준다")
	void savedRevision(SettlementStatus status) {
		ReflectionTestUtils.setField(settlement, "status", status);
		ReflectionTestUtils.setField(settlement, "currentRevisionNo", 1);
		givenSettlement();
		SettlementRevision revision = SettlementRevision.create(settlement, 1, member(1L),
				LocalDateTime.of(2026, 12, 20, 22, 0), false);
		ReflectionTestUtils.setField(revision, "id", REVISION_ID);
		given(revisionRepository.findBySettlementIdAndRevisionNo(SETTLEMENT_ID, 1)).willReturn(Optional.of(revision));
		given(memberShareRepository.findAllByRevisionIdOrderByMemberIdAsc(REVISION_ID)).willReturn(List.of(
				RevisionMemberShare.create(revision, member(1L), 10_000L, 10_000L),
				RevisionMemberShare.create(revision, member(2L), 10_000L, -10_000L)));
		given(transferRepository.findAllByRevisionIdOrderByIdAsc(REVISION_ID)).willReturn(List.of(
				Transfer.create(revision, member(2L), member(1L), 10_000L)));

		SettlementResponse response = service.get(MEETING_ID, MEMBER_ID);

		assertThat(response).isEqualTo(new SettlementResponse(status, 1, false,
				List.of(new SettlementMemberResponse(1L, 10_000L, 10_000L),
						new SettlementMemberResponse(2L, 10_000L, -10_000L)),
				List.of(new SettlementTransferResponse(2L, 1L, 10_000L)),
				List.of()));
		verifyNoInteractions(inputAssembler);
	}

	@Test
	@DisplayName("그룹 멤버가 아니면 403이고 정산을 읽지 않는다")
	void notMember() {
		given(meetingAccessChecker.requireMember(MEETING_ID, MEMBER_ID))
				.willThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

		assertThatThrownBy(() -> service.get(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
		verifyNoInteractions(settlementRepository);
	}

	@Test
	@DisplayName("정산 행이 없으면 MEETING_NOT_FOUND다")
	void settlementMissing() {
		given(settlementRepository.findByMeetingId(MEETING_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(MEETING_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEETING_NOT_FOUND);
	}

	@Test
	@DisplayName("확정 상태인데 리비전이 없으면 불변식 위반이다")
	void revisionMissing() {
		ReflectionTestUtils.setField(settlement, "status", SettlementStatus.CONFIRMED);
		ReflectionTestUtils.setField(settlement, "currentRevisionNo", 1);
		givenSettlement();
		given(revisionRepository.findBySettlementIdAndRevisionNo(SETTLEMENT_ID, 1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(MEETING_ID, MEMBER_ID)).isInstanceOf(IllegalStateException.class);
	}

	private void givenSettlement() {
		given(settlementRepository.findByMeetingId(MEETING_ID)).willReturn(Optional.of(settlement));
	}

	private Member member(Long id) {
		Member member = Member.ofKakao(id, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
