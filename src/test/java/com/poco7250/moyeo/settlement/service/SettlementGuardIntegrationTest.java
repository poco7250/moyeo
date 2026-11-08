package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poco7250.moyeo.global.concurrency.OptimisticRetryExecutor;
import com.poco7250.moyeo.global.event.RealtimeMessage;
import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.group.repository.GroupRepository;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.settlement.domain.ChangeLog;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.dto.ChangeLogSnapshot;
import com.poco7250.moyeo.settlement.dto.SettlementReopenedResponse;
import com.poco7250.moyeo.settlement.repository.ChangeLogRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 커밋 순서와 version 충돌을 봐야 해서 테스트 트랜잭션을 쓰지 않고 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementGuardIntegrationTest {

	private static final Long ACTOR_ID = 7L;

	@Autowired
	SettlementGuard settlementGuard;

	@Autowired
	ChangeLogRecorder changeLogRecorder;

	@Autowired
	OptimisticRetryExecutor retryExecutor;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	ChangeLogRepository changeLogRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	@MockitoSpyBean
	SimpMessagingTemplate messagingTemplate;

	private Group group;
	private Meeting meeting;
	private Settlement settlement;

	@BeforeEach
	void setUp() {
		group = groupRepository.save(Group.create("대학 동기"));
		meeting = meetingRepository.save(Meeting.create(
				group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0), null, null, 1L));
		settlement = settlementRepository.save(Settlement.createDraft(meeting));
		clearInvocations(messagingTemplate);
	}

	@AfterEach
	void tearDown() {
		// ChangeLog는 append-only라 저장소에 삭제 경로가 없어 SQL로 지운다
		jdbcTemplate.update("delete from change_log where meeting_id = ?", meeting.getId());
		settlementRepository.deleteById(settlement.getId());
		meetingRepository.deleteById(meeting.getId());
		groupRepository.deleteById(group.getId());
	}

	@Test
	@DisplayName("같은 모임에 동시 쓰기 2건이면 하나만 성공하고 다른 쪽은 낙관적 락 충돌이다")
	void concurrentWrites() throws Exception {
		Long versionBefore = currentVersion();
		CountDownLatch bothLocked = new CountDownLatch(2);

		List<CompletableFuture<Throwable>> writes = List.of(lockAndWait(bothLocked), lockAndWait(bothLocked));

		List<Throwable> failures = writes.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
		assertThat(failures).hasSize(1);
		assertThat(failures.getFirst()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(currentVersion()).isGreaterThan(versionBefore);
	}

	@Test
	@DisplayName("첫 시도가 충돌해도 재시도는 새 트랜잭션에서 최신 version을 읽어 성공한다")
	void retryUsesNewTransaction() {
		TransactionTemplate competitor = new TransactionTemplate(transactionManager);
		competitor.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		AtomicInteger attempts = new AtomicInteger();

		String result = retryExecutor.execute(() -> transactionTemplate.execute(status -> {
			settlementGuard.lockForAmountChange(meeting.getId());
			if (attempts.incrementAndGet() == 1) {
				// 이 트랜잭션이 읽은 뒤 다른 트랜잭션이 먼저 커밋해 version을 올린다
				competitor.executeWithoutResult(
						inner -> settlementGuard.lockForAmountChange(meeting.getId()));
			}
			return "완료";
		}));

		assertThat(result).isEqualTo("완료");
		assertThat(attempts).hasValue(2);
	}

	@Test
	@DisplayName("CONFIRMED 정산은 같은 트랜잭션에서 REOPENED가 되고 커밋 후에만 이벤트가 나간다")
	void reopenBroadcastsAfterCommit() {
		confirmSettlement();

		transactionTemplate.executeWithoutResult(status -> {
			settlementGuard.markAmountChanged(meeting.getId(), ACTOR_ID);
			assertThat(settlementRepository.findByMeetingId(meeting.getId()).orElseThrow().getStatus())
					.isEqualTo(SettlementStatus.REOPENED);
			verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
		});

		assertThat(currentStatus()).isEqualTo(SettlementStatus.REOPENED);
		verify(messagingTemplate, times(1)).convertAndSend("/topic/meetings/" + meeting.getId(),
				new RealtimeMessage(SettlementReopenedEvent.TYPE, new SettlementReopenedResponse(ACTOR_ID)));
	}

	@Test
	@DisplayName("커밋하면 같은 트랜잭션의 변경 로그가 변경 후 상태와 함께 1건 남는다")
	void changeLogCommitted() {
		confirmSettlement();

		transactionTemplate.executeWithoutResult(status -> markAndRecord());

		List<ChangeLog> logs = changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId());
		assertThat(logs).hasSize(1);
		assertThat(logs.getFirst().getSettlementStatusAfter()).isEqualTo(SettlementStatus.REOPENED);
		assertThat(logs.getFirst().getBeforeJson()).isEqualTo("{\"amount\":1000}");
	}

	@Test
	@DisplayName("롤백하면 변경 로그·상태 전이·이벤트가 모두 없다")
	void rollbackLeavesNothing() {
		confirmSettlement();

		transactionTemplate.executeWithoutResult(status -> {
			markAndRecord();
			status.setRollbackOnly();
		});

		assertThat(changeLogRepository.findAllByMeetingIdOrderByChangedAtAscIdAsc(meeting.getId())).isEmpty();
		assertThat(currentStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	@DisplayName("lockOnly는 version만 올리고 상태 변경·이벤트가 없다")
	void lockOnlyBumpsVersion() {
		confirmSettlement();
		Long versionBefore = currentVersion();

		transactionTemplate.executeWithoutResult(status -> settlementGuard.lockOnly(meeting.getId()));

		assertThat(currentVersion()).isGreaterThan(versionBefore);
		assertThat(currentStatus()).isEqualTo(SettlementStatus.CONFIRMED);
		verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	@DisplayName("트랜잭션 없이 Guard와 Recorder를 부르면 바로 실패한다")
	void requiresTransaction() {
		assertThatThrownBy(() -> settlementGuard.lockForAmountChange(meeting.getId()))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> changeLogRecorder.record(meeting.getId(), ACTOR_ID, ChangeLogEntityType.EXPENSE, 1L,
				ChangeType.DELETE, null, null))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

	private CompletableFuture<Throwable> lockAndWait(CountDownLatch bothLocked) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				transactionTemplate.executeWithoutResult(status -> {
					settlementGuard.lockForAmountChange(meeting.getId());
					bothLocked.countDown();
					await(bothLocked);
				});
				return null;
			} catch (RuntimeException e) {
				return e;
			}
		});
	}

	private void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private void markAndRecord() {
		settlementGuard.markAmountChanged(meeting.getId(), ACTOR_ID);
		changeLogRecorder.record(meeting.getId(), ACTOR_ID, ChangeLogEntityType.EXPENSE, 1L, ChangeType.UPDATE,
				new AmountSnapshot(1000L), new AmountSnapshot(2000L));
	}

	// 확정 전이는 Task 024에서 생기므로 상태를 직접 맞춘다
	private void confirmSettlement() {
		transactionTemplate.executeWithoutResult(status -> ReflectionTestUtils.setField(
				settlementRepository.findById(settlement.getId()).orElseThrow(),
				"status", SettlementStatus.CONFIRMED));
	}

	private Long currentVersion() {
		return settlementRepository.findById(settlement.getId()).orElseThrow().getVersion();
	}

	private SettlementStatus currentStatus() {
		return settlementRepository.findById(settlement.getId()).orElseThrow().getStatus();
	}

	/** 테스트용 화이트리스트 DTO */
	record AmountSnapshot(long amount) implements ChangeLogSnapshot {
	}
}
