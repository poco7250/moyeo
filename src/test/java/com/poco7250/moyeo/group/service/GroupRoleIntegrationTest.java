package com.poco7250.moyeo.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
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
import com.poco7250.moyeo.place.dto.PlaceInput;
import com.poco7250.moyeo.place.service.RoundPlaceService;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementStatus;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import com.poco7250.moyeo.settlement.service.SettlementConfirmService;
import com.poco7250.moyeo.support.DatabaseCleaner;
import com.poco7250.moyeo.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 그룹 역할(PRD G-4) 통합 테스트. 방장 A(철수), 멤버 B(민수)·C(영희), C가 만든 모임으로 역할별 권한과
 * 방장 양도를 실제 커밋으로 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GroupRoleIntegrationTest {

	@Autowired
	GroupRoleService groupRoleService;

	@Autowired
	SettlementConfirmService settlementConfirmService;

	@Autowired
	RoundPlaceService roundPlaceService;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	GroupRepository groupRepository;

	@Autowired
	GroupMemberRepository groupMemberRepository;

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	RoundRepository roundRepository;

	@Autowired
	SettlementRepository settlementRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private Member owner;
	private Member minsu;
	private Member younghee;
	private Group group;
	private Meeting meeting;
	private Round round;

	@BeforeEach
	void setUp() {
		owner = memberRepository.save(Member.ofKakao(5801L, "철수", null, null));
		minsu = memberRepository.save(Member.ofKakao(5802L, "민수", null, null));
		younghee = memberRepository.save(Member.ofKakao(5803L, "영희", null, null));
		group = groupRepository.save(Group.create("대학 동기"));
		groupMemberRepository.saveAll(List.of(GroupMember.join(group, owner, "철수", GroupRole.OWNER),
				GroupMember.join(group, minsu, "민수", GroupRole.MEMBER),
				GroupMember.join(group, younghee, "영희", GroupRole.MEMBER)));
		meeting = meetingRepository.save(Meeting.create(group, "연말 모임", LocalDateTime.of(2099, 12, 20, 18, 0),
				null, null, younghee.getId()));
		settlementRepository.save(Settlement.createDraft(meeting));
		round = roundRepository.save(Round.create(meeting, 1, "밥"));
	}

	@AfterEach
	void tearDown() {
		new DatabaseCleaner(jdbcTemplate).truncateAll();
	}

	@Test
	@DisplayName("총무는 남의 모임 정산을 확정하지만 장소 확정은 403 NOT_MEETING_HOST다")
	void treasurerSettlesOnly() {
		groupRoleService.changeRole(group.getId(), owner.getId(), minsu.getId(), GroupRole.TREASURER);

		assertRejected(() -> roundPlaceService.confirmDirectly(round.getId(), minsu.getId(), place()),
				ErrorCode.NOT_MEETING_HOST);
		assertThat(settlementConfirmService.confirm(meeting.getId(), minsu.getId(), true).status())
				.isEqualTo(SettlementStatus.CONFIRMED);
	}

	@Test
	@DisplayName("총무를 해제하면 정산 권한도 잃는다")
	void treasurerRevoked() {
		groupRoleService.changeRole(group.getId(), owner.getId(), minsu.getId(), GroupRole.TREASURER);
		groupRoleService.changeRole(group.getId(), owner.getId(), minsu.getId(), GroupRole.MEMBER);

		assertRejected(() -> settlementConfirmService.confirm(meeting.getId(), minsu.getId(), true),
				ErrorCode.NOT_MEETING_HOST);
	}

	@Test
	@DisplayName("방장은 남의 모임에서 장소 확정·정산 확정을 하고, 방장을 넘기면 그 권한을 잃는다")
	void ownerActsAsHostUntilTransfer() {
		assertThat(roundPlaceService.confirmDirectly(round.getId(), owner.getId(), place())).isNotNull();

		groupRoleService.transferOwner(group.getId(), owner.getId(), minsu.getId());

		assertThat(roleOf(owner)).isEqualTo(GroupRole.MEMBER);
		assertThat(roleOf(minsu)).isEqualTo(GroupRole.OWNER);
		assertRejected(() -> settlementConfirmService.confirm(meeting.getId(), owner.getId(), true),
				ErrorCode.NOT_MEETING_HOST);
		assertRejected(() -> groupRoleService.changeRole(group.getId(), owner.getId(), younghee.getId(),
				GroupRole.TREASURER), ErrorCode.NOT_GROUP_OWNER);
		assertThat(settlementConfirmService.confirm(meeting.getId(), minsu.getId(), true).status())
				.isEqualTo(SettlementStatus.CONFIRMED);
	}

	@RepeatedTest(3)
	@DisplayName("방장이 두 멤버에게 동시에 방장을 넘겨도 방장은 1명만 남는다")
	void concurrentTransfer() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		CompletableFuture<Boolean> toMinsu = transferAsync(start, minsu);
		CompletableFuture<Boolean> toYounghee = transferAsync(start, younghee);
		start.countDown();

		boolean minsuWon = toMinsu.get(10, TimeUnit.SECONDS);
		boolean youngheeWon = toYounghee.get(10, TimeUnit.SECONDS);

		assertThat(minsuWon ^ youngheeWon).as("한쪽만 성공해야 한다").isTrue();
		assertThat(groupMemberRepository.findAllByGroupIdOrderByIdAsc(group.getId()))
				.filteredOn(GroupMember::isOwner).hasSize(1)
				.first().extracting(gm -> gm.getMember().getId())
				.isEqualTo(minsuWon ? minsu.getId() : younghee.getId());
	}

	/**
	 * 신호를 받으면 방장 양도를 시도하고, 성공 여부를 돌려준다. 늦은 쪽은 방장이 아니게 된 뒤라 NOT_GROUP_OWNER여야 한다.
	 */
	private CompletableFuture<Boolean> transferAsync(CountDownLatch start, Member target) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				start.await();
				groupRoleService.transferOwner(group.getId(), owner.getId(), target.getId());
				return true;
			} catch (BusinessException e) {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_GROUP_OWNER);
				return false;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		});
	}

	private GroupRole roleOf(Member member) {
		return groupMemberRepository.findByGroupIdAndMemberId(group.getId(), member.getId()).orElseThrow().getRole();
	}

	private static PlaceInput place() {
		return new PlaceInput("강남 식당", null, null);
	}

	private void assertRejected(ThrowingCallable call, ErrorCode errorCode) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(errorCode);
	}
}
