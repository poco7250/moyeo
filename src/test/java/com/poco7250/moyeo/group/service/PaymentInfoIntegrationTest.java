package com.poco7250.moyeo.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.dto.PaymentInfoRequest;
import com.poco7250.moyeo.group.dto.PaymentInfoResponse;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.ExpenseRequest;
import com.poco7250.moyeo.settlement.dto.SettlementResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.service.ExpenseService;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.settlement.service.SettlementQueryService;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 송금 정보 저장·암호화와 정산 송금 안내 연결(PRD G-5·S-7)을 실제 DB로 본다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentInfoIntegrationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2099, 12, 20, 18, 0);
	private static final String HOST_ACCOUNT = "110123456789";

	@Autowired
	GroupMemberPaymentService paymentService;

	@Autowired
	SettlementQueryService queryService;

	@Autowired
	SettlementConfirmService confirmService;

	@Autowired
	ExpenseService expenseService;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	AttendanceRepository attendanceRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	ChangeLogRepository changeLogRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Member host;
	private Member other;
	private Member third;
	private Member outsider;
	private Meeting meeting;
	private Settlement settlement;
	private Expense expense;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		host = saveMember("철수");
		other = saveMember("지수");
		third = saveMember("민수");
		outsider = saveMember("외부인");
		groupMemberRepository.saveAll(List.of(join(group, host), join(group, other), join(group, third)));
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", NOW, null, null, host.getId()));
		Round round = roundRepository.save(Round.create(meeting, 1, "밥"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		attendanceRepository.saveAll(List.of(attend(round, host), attend(round, other), attend(round, third)));
		// 철수가 9만 원을 냈으니 송금 안내는 지수→철수, 민수→철수 — 받는 사람은 철수뿐이다
		expense = expenseRepository.save(Expense.create(round, host, 90_000L, "고기"));
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("저장·교체·조회·삭제가 되고, DB에는 계좌번호가 암호문으로만 남는다")
	void storeEncrypted() {
		PaymentInfoResponse saved = paymentService.replaceMine(group.getId(), host.getId(), hostAccount());

		assertThat(saved).isEqualTo(new PaymentInfoResponse(host.getId(), "국민", HOST_ACCOUNT, "철수", null, null));
		String stored = jdbcTemplate.queryForObject("select encrypted_account_number from group_member_payment",
				String.class);
		assertThat(stored).isNotBlank().doesNotContain(HOST_ACCOUNT);

		paymentService.replaceMine(group.getId(), host.getId(),
				new PaymentInfoRequest(null, null, null, null, "https://qr.kakaopay.com/abc"));
		assertThat(paymentService.getMine(group.getId(), host.getId()))
				.isEqualTo(new PaymentInfoResponse(host.getId(), null, null, null, null, "https://qr.kakaopay.com/abc"));

		paymentService.deleteMine(group.getId(), host.getId());
		assertThat(paymentService.getMine(group.getId(), host.getId())).isNull();
		assertErrorCode(() -> paymentService.deleteMine(group.getId(), host.getId()), ErrorCode.PAYMENT_INFO_NOT_FOUND);
	}

	@Test
	@DisplayName("비멤버는 403이고, 같은 회원이라도 다른 그룹의 송금 정보는 따로다")
	void scopedToGroupMember() {
		Group otherGroup = groupRepository.save(Group.create("회사"));
		groupMemberRepository.save(join(otherGroup, host));
		paymentService.replaceMine(group.getId(), host.getId(), hostAccount());

		assertThat(paymentService.getMine(otherGroup.getId(), host.getId())).isNull();
		assertErrorCode(() -> paymentService.getMine(group.getId(), outsider.getId()), ErrorCode.NOT_GROUP_MEMBER);
		assertErrorCode(() -> paymentService.replaceMine(group.getId(), outsider.getId(), hostAccount()),
				ErrorCode.NOT_GROUP_MEMBER);
	}

	@Test
	@DisplayName("정산 조회·확정 응답의 payees에는 송금 안내 받는 사람의 정보만 실린다")
	void payeesOnlyReceivers() {
		paymentService.replaceMine(group.getId(), host.getId(), hostAccount());
		// 지수는 보내기만 하는 사람이라 정보가 있어도 실리지 않는다
		paymentService.replaceMine(group.getId(), other.getId(),
				new PaymentInfoRequest(null, null, null, "https://toss.me/jisoo", null));
		PaymentInfoResponse expected = new PaymentInfoResponse(host.getId(), "국민", HOST_ACCOUNT, "철수", null, null);

		SettlementResponse preview = queryService.get(meeting.getId(), third.getId());
		SettlementResponse confirmed = confirmService.confirm(meeting.getId(), host.getId(), false);

		assertThat(preview.payees()).containsExactly(expected);
		assertThat(confirmed.payees()).containsExactly(expected);
		assertThat(queryService.get(meeting.getId(), other.getId()).payees()).containsExactly(expected);
	}

	@Test
	@DisplayName("재확정 실시간 이벤트에는 계좌번호가 실리지 않는다")
	void reconfirmEventHasNoAccount() {
		paymentService.replaceMine(group.getId(), host.getId(), hostAccount());
		confirmService.confirm(meeting.getId(), host.getId(), false);
		expenseService.update(expense.getId(), host.getId(), new ExpenseRequest(120_000L, host.getId(), "고기"));
		clearInvocations(messagingTemplate);

		SettlementResponse reconfirmed = confirmService.confirm(meeting.getId(), host.getId(), false);

		assertThat(reconfirmed.payees()).extracting(PaymentInfoResponse::accountNumber).containsExactly(HOST_ACCOUNT);
		ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), payloads.capture());
		assertThat(payloads.getAllValues()).isNotEmpty()
				.allSatisfy(payload -> assertThat(String.valueOf(payload)).doesNotContain(HOST_ACCOUNT, "국민"));
	}

	@Test
	@DisplayName("확정된 정산에서 송금 정보를 바꿔도 CONFIRMED 그대로이고 변경 로그·실시간 알림이 없다")
	void doesNotReopen() {
		confirmService.confirm(meeting.getId(), host.getId(), false);
		int logsBefore = changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId()).size();
		clearInvocations(messagingTemplate);

		paymentService.replaceMine(group.getId(), host.getId(), hostAccount());
		paymentService.deleteMine(group.getId(), host.getId());

		assertThat(settlementRepository.findById(settlement.getId()).orElseThrow().getStatus())
				.isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).hasSize(logsBefore);
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	private static PaymentInfoRequest hostAccount() {
		return new PaymentInfoRequest("국민", "110-123-456789", "철수", null, null);
	}

	private static void assertErrorCode(Runnable call, ErrorCode errorCode) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private static GroupMember join(Group target, Member member) {
		return GroupMember.join(target, member, member.getNickname(), GroupRole.MEMBER);
	}

	private static Attendance attend(Round round, Member member) {
		return Attendance.respond(round, member, AttendanceStatus.ATTEND, NOW);
	}
}
