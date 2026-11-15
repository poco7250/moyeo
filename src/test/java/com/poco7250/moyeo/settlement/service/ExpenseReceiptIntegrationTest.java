package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.global.storage.ReceiptStorageProperties;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.domain.GroupRole;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.ExpenseReceiptChangedResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseReceiptResponse;
import com.poco7250.moyeo.settlement.dto.ExpenseResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseReceiptRepository;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커밋·롤백 뒤 파일 정리와 커밋 후 이벤트를 실제로 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpenseReceiptIntegrationTest {

	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00};

	@Autowired
	ExpenseReceiptService expenseReceiptService;

	@Autowired
	ExpenseService expenseService;

	@Autowired
	ExpenseReceiptRepository expenseReceiptRepository;

	@Autowired
	ExpenseRepository expenseRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ChangeLogRepository changeLogRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	ReceiptStorageProperties storageProperties;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Member payer;
	private Member other;
	private Member outsider;
	private Meeting meeting;
	private Settlement settlement;
	private Expense expense;

	@BeforeEach
	void setUp() throws IOException {
		clearStorage();
		Group group = groupRepository.save(Group.create("대학 동기"));
		payer = saveMember("철수");
		other = saveMember("지수");
		outsider = saveMember("외부인");
		groupMemberRepository.saveAll(List.of(join(group, payer), join(group, other)));
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, payer.getId()));
		Round round = roundRepository.save(Round.create(meeting, 1, "밥"));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		expense = expenseRepository.save(Expense.create(round, payer, 50_000L, "고기"));
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() throws IOException {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
		clearStorage();
	}

	@Test
	@DisplayName("올리면 파일이 저장되고 지출 목록에 사진 정보가 붙으며 커밋 후 알림이 1회 나간다")
	void upload() throws IOException {
		ExpenseReceiptResponse response = expenseReceiptService.upload(expense.getId(), other.getId(), file(JPEG));

		assertThat(response.contentType()).isEqualTo("image/jpeg");
		assertThat(response.size()).isEqualTo(JPEG.length);
		assertThat(response.uploadedBy()).isEqualTo(other.getId());
		assertThat(response.url()).isEqualTo("/api/expenses/" + expense.getId() + "/receipt?v=0");
		assertThat(storedFiles()).hasSize(1);
		assertThat(expenseReceiptService.load(expense.getId(), payer.getId()).resource().getContentAsByteArray())
				.containsExactly(JPEG);
		List<ExpenseResponse> expenses = expenseService.list(meeting.getId(), payer.getId());
		assertThat(expenses).singleElement().extracting(ExpenseResponse::receipt).isEqualTo(response);
		verifyChangedEventOnce(response);
	}

	@Test
	@DisplayName("다시 올리면 교체되고 이전 파일은 커밋 뒤 지워지며 url 버전이 바뀐다")
	void replace() throws IOException {
		expenseReceiptService.upload(expense.getId(), payer.getId(), file(JPEG));
		Path first = storedFiles().getFirst();

		ExpenseReceiptResponse replaced = expenseReceiptService.upload(expense.getId(), other.getId(), file(PNG));

		assertThat(replaced.contentType()).isEqualTo("image/png");
		assertThat(replaced.url()).endsWith("?v=1");
		assertThat(storedFiles()).singleElement().isNotEqualTo(first);
		assertThat(storedFiles().getFirst().toString()).endsWith(".png");
		assertThat(expenseReceiptRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("지우면 행과 파일이 사라지고 null 알림이 나가며, 다시 지우거나 조회하면 404 RECEIPT_NOT_FOUND")
	void deleteReceipt() throws IOException {
		expenseReceiptService.upload(expense.getId(), payer.getId(), file(JPEG));
		clearInvocations(messagingTemplate);

		expenseReceiptService.delete(expense.getId(), other.getId());

		assertThat(expenseReceiptRepository.count()).isZero();
		assertThat(storedFiles()).isEmpty();
		verifyChangedEventOnce(null);
		assertErrorCode(() -> expenseReceiptService.delete(expense.getId(), payer.getId()),
				ErrorCode.RECEIPT_NOT_FOUND);
		assertErrorCode(() -> expenseReceiptService.load(expense.getId(), payer.getId()),
				ErrorCode.RECEIPT_NOT_FOUND);
	}

	@Test
	@DisplayName("지출을 삭제하면 영수증 행과 파일도 같이 지워진다")
	void deletedWithExpense() throws IOException {
		expenseReceiptService.upload(expense.getId(), payer.getId(), file(JPEG));

		expenseService.delete(expense.getId(), payer.getId());

		assertThat(expenseRepository.findById(expense.getId())).isEmpty();
		assertThat(expenseReceiptRepository.count()).isZero();
		assertThat(storedFiles()).isEmpty();
	}

	@Test
	@DisplayName("확정된 정산에서 올리고 지워도 CONFIRMED 그대로이고 변경 로그가 남지 않는다")
	void doesNotReopenSettlement() {
		Settlement current = settlementRepository.findById(settlement.getId()).orElseThrow();
		ReflectionTestUtils.setField(current, "status", SettlementStatus.CONFIRMED);
		settlementRepository.save(current);

		expenseReceiptService.upload(expense.getId(), payer.getId(), file(JPEG));
		expenseReceiptService.delete(expense.getId(), payer.getId());

		assertThat(settlementRepository.findById(settlement.getId()).orElseThrow().getStatus())
				.isEqualTo(SettlementStatus.CONFIRMED);
		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).isEmpty();
	}

	@Test
	@DisplayName("비멤버는 403, 없는 지출은 404이고 파일을 쓰지 않는다")
	void authorization() throws IOException {
		assertErrorCode(() -> expenseReceiptService.upload(expense.getId(), outsider.getId(), file(JPEG)),
				ErrorCode.NOT_GROUP_MEMBER);
		assertErrorCode(() -> expenseReceiptService.load(expense.getId(), outsider.getId()),
				ErrorCode.NOT_GROUP_MEMBER);
		assertErrorCode(() -> expenseReceiptService.upload(Long.MAX_VALUE, payer.getId(), file(JPEG)),
				ErrorCode.EXPENSE_NOT_FOUND);
		assertThat(storedFiles()).isEmpty();
	}

	@Test
	@DisplayName("이미지가 아니면 400, 10MB를 넘으면 413이고 파일을 쓰지 않는다")
	void invalidFile() throws IOException {
		byte[] tooLarge = new byte[(int) (10L * 1024 * 1024 + 1)];
		System.arraycopy(JPEG, 0, tooLarge, 0, JPEG.length);

		assertErrorCode(() -> expenseReceiptService.upload(expense.getId(), payer.getId(),
				new MockMultipartFile("file", "a.jpg", "image/jpeg", "not image".getBytes())),
				ErrorCode.RECEIPT_INVALID_TYPE);
		assertErrorCode(() -> expenseReceiptService.upload(expense.getId(), payer.getId(), file(tooLarge)),
				ErrorCode.RECEIPT_TOO_LARGE);
		assertThat(storedFiles()).isEmpty();
	}

	@Test
	@DisplayName("업로드가 롤백되면 새로 쓴 파일을 지우고 기존 사진은 그대로 둔다")
	void rollbackCleansNewFile() throws IOException {
		expenseReceiptService.upload(expense.getId(), payer.getId(), file(JPEG));
		List<Path> before = storedFiles();

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			expenseReceiptService.upload(expense.getId(), payer.getId(), file(PNG));
			throw new IllegalStateException("뒤이은 작업 실패");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(storedFiles()).isEqualTo(before);
		assertThat(expenseReceiptService.findResponse(expense.getId()).contentType()).isEqualTo("image/jpeg");
	}

	private void verifyChangedEventOnce(ExpenseReceiptResponse receipt) {
		verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/meetings/" + meeting.getId()), any(Object.class));
		verify(messagingTemplate).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(ExpenseReceiptChangedEvent.TYPE,
						new ExpenseReceiptChangedResponse(expense.getId(), receipt)));
	}

	private static void assertErrorCode(Runnable call, ErrorCode errorCode) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
	}

	private static MockMultipartFile file(byte[] content) {
		return new MockMultipartFile("file", "receipt", "application/octet-stream", content);
	}

	private Path baseDir() {
		return Path.of(storageProperties.localBaseDir()).toAbsolutePath().normalize();
	}

	private List<Path> storedFiles() throws IOException {
		if (!Files.isDirectory(baseDir())) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(baseDir())) {
			return files.sorted().toList();
		}
	}

	private void clearStorage() throws IOException {
		for (Path path : storedFiles()) {
			Files.deleteIfExists(path);
		}
	}

	private Member saveMember(String nickname) {
		return memberRepository.save(Member.ofKakao(System.nanoTime(), nickname, null, null));
	}

	private static GroupMember join(Group group, Member member) {
		return GroupMember.join(group, member, member.getNickname(), GroupRole.MEMBER);
	}
}
