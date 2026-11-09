package com.poco7250.moyeo.settlement.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShareDistributorTest {

	private static final long EXPENSE_ID = 10L;
	private static final long CHEOLSU = 1L;
	private static final long MINSU = 2L;
	private static final long YOUNGHEE = 3L;
	private static final long JISU = 4L;
	private static final long ABSENT = 5L;

	private static final Set<Long> FOUR = Set.of(CHEOLSU, MINSU, YOUNGHEE, JISU);
	private static final Set<Long> THREE = Set.of(CHEOLSU, MINSU, YOUNGHEE);

	private static ExpenseDistribution distribute(long amount, long payerId, Set<Long> attendees, ShareAdjustment... adjustments) {
		return ShareDistributor.distribute(new ExpenseInput(EXPENSE_ID, amount, payerId, attendees, List.of(adjustments)));
	}

	private static ShareAdjustment excluded(long memberId) {
		return new ShareAdjustment(memberId, true, null, null);
	}

	private static ShareAdjustment fixed(long memberId, long amount) {
		return new ShareAdjustment(memberId, false, amount, null);
	}

	private static ShareAdjustment weight(long memberId, int weight) {
		return new ShareAdjustment(memberId, false, null, weight);
	}

	@Test
	@DisplayName("S-2: 참석자 4명이 100,000원을 25,000원씩 나누고 불참자는 분배 대상에 없다")
	void splitsEquallyAmongAttendees() {
		ExpenseDistribution result = distribute(100_000L, CHEOLSU, FOUR);

		assertThat(result.shares()).containsOnly(
				entry(CHEOLSU, 25_000L), entry(MINSU, 25_000L), entry(YOUNGHEE, 25_000L), entry(JISU, 25_000L));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("S-3: 제외자는 0원이고 3명 분배의 초과분 200원은 결제자 몫에서 뺀다")
	void excludedMemberPaysNothing() {
		ExpenseDistribution result = distribute(100_000L, CHEOLSU, FOUR, excluded(JISU));

		assertThat(result.shares()).containsOnly(
				entry(CHEOLSU, 33_200L), entry(MINSU, 33_400L), entry(YOUNGHEE, 33_400L), entry(JISU, 0L));
	}

	@Test
	@DisplayName("S-8: 1원이 넘쳐 올림된 금액의 초과분도 결제자 몫에서 뺀다")
	void deductsExcessWhenOneWonOver() {
		ExpenseDistribution result = distribute(100_001L, CHEOLSU, FOUR);

		assertThat(result.shares()).containsOnly(
				entry(CHEOLSU, 24_701L), entry(MINSU, 25_100L), entry(YOUNGHEE, 25_100L), entry(JISU, 25_100L));
	}

	@Test
	@DisplayName("S-8: 1억 원도 정확히 나눈다")
	void handlesMaximumAmount() {
		ExpenseDistribution result = distribute(100_000_000L, CHEOLSU, THREE);

		assertThat(result.shares()).containsOnly(
				entry(CHEOLSU, 33_333_200L), entry(MINSU, 33_333_400L), entry(YOUNGHEE, 33_333_400L));
	}

	@Test
	@DisplayName("초과분이 결제자 몫보다 크면 memberId 내림차순으로 0원까지 뺀다")
	void deductsRemainingExcessByDescendingMemberId() {
		ExpenseDistribution result = distribute(50L, CHEOLSU, THREE);

		assertThat(result.shares()).containsOnly(entry(CHEOLSU, 0L), entry(MINSU, 50L), entry(YOUNGHEE, 0L));
	}

	@Test
	@DisplayName("결제자가 불참이면 초과분은 memberId가 가장 큰 참석자 몫에서 뺀다")
	void deductsFromLargestMemberIdWhenPayerAbsent() {
		ExpenseDistribution result = distribute(100_000L, ABSENT, THREE);

		assertThat(result.shares()).containsOnly(
				entry(CHEOLSU, 33_400L), entry(MINSU, 33_400L), entry(YOUNGHEE, 33_200L));
	}

	@Test
	@DisplayName("가중치에 비례해 나눈다")
	void splitsByWeight() {
		ExpenseDistribution result = distribute(100_000L, CHEOLSU, THREE, weight(CHEOLSU, 2));

		assertThat(result.shares()).containsOnly(
				entry(CHEOLSU, 50_000L), entry(MINSU, 25_000L), entry(YOUNGHEE, 25_000L));
	}

	@Test
	@DisplayName("가중치 몫도 100원 올림하고 초과분은 결제자 몫에서 뺀다")
	void roundsWeightedShareUp() {
		ExpenseDistribution result = distribute(100_000L, CHEOLSU, Set.of(CHEOLSU, MINSU), weight(CHEOLSU, 2));

		assertThat(result.shares()).containsOnly(entry(CHEOLSU, 66_600L), entry(MINSU, 33_400L));
	}

	@Test
	@DisplayName("고정 금액을 먼저 떼고 나머지를 분배한다")
	void chargesFixedAmountFirst() {
		ExpenseDistribution result = distribute(100_000L, CHEOLSU, FOUR, fixed(JISU, 10_000L));

		assertThat(result.shares()).containsOnly(
				entry(CHEOLSU, 30_000L), entry(MINSU, 30_000L), entry(YOUNGHEE, 30_000L), entry(JISU, 10_000L));
	}

	@Test
	@DisplayName("결제자가 고정 금액이면 그 몫은 그대로 두고 초과분은 다른 사람에게서 뺀다")
	void keepsFixedAmountOfPayer() {
		ExpenseDistribution result = distribute(100_000L, CHEOLSU, FOUR, fixed(CHEOLSU, 10_000L), excluded(JISU));

		assertThat(result.shares()).containsOnly(
				entry(CHEOLSU, 10_000L), entry(MINSU, 45_000L), entry(YOUNGHEE, 45_000L), entry(JISU, 0L));

		ExpenseDistribution rounded = distribute(100_001L, CHEOLSU, THREE, fixed(CHEOLSU, 10_000L));

		assertThat(rounded.shares()).containsOnly(
				entry(CHEOLSU, 10_000L), entry(MINSU, 45_100L), entry(YOUNGHEE, 44_901L));
	}

	@Test
	@DisplayName("고정 금액 합이 지출을 넘으면 memberId 오름차순으로 남은 금액까지만 부담하고 경고한다")
	void capsFixedAmountsAndWarns() {
		ExpenseDistribution result = distribute(10_000L, CHEOLSU, Set.of(MINSU, YOUNGHEE), fixed(MINSU, 8_000L), fixed(YOUNGHEE, 5_000L));

		assertThat(result.shares()).containsOnly(entry(MINSU, 8_000L), entry(YOUNGHEE, 2_000L));
		assertThat(result.warnings()).containsExactly(
				new SettlementWarning(SettlementWarningType.FIXED_AMOUNT_OVERFLOW, EXPENSE_ID));
	}

	@Test
	@DisplayName("고정 금액 합이 지출과 같으면 다른 참석자는 0원이고 경고가 없다")
	void leavesNothingWhenFixedEqualsAmount() {
		ExpenseDistribution result = distribute(10_000L, CHEOLSU, THREE, fixed(MINSU, 10_000L));

		assertThat(result.shares()).containsOnly(entry(CHEOLSU, 0L), entry(MINSU, 10_000L), entry(YOUNGHEE, 0L));
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("남은 금액을 나눌 사람이 없으면 결제자가 부담하고 경고한다")
	void payerBearsRemainderWithoutPool() {
		ExpenseDistribution result = distribute(10_000L, CHEOLSU, Set.of(MINSU), fixed(MINSU, 3_000L));

		assertThat(result.shares()).containsOnly(entry(CHEOLSU, 7_000L), entry(MINSU, 3_000L));
		assertThat(result.warnings()).containsExactly(
				new SettlementWarning(SettlementWarningType.NO_SHARE_TARGET, EXPENSE_ID));
	}

	@Test
	@DisplayName("참석자가 없으면 결제자가 전액 부담하고 경고한다")
	void payerBearsAllWithoutAttendees() {
		ExpenseDistribution result = distribute(10_000L, CHEOLSU, Set.of());

		assertThat(result.shares()).containsOnly(entry(CHEOLSU, 10_000L));
		assertThat(result.warnings()).containsExactly(
				new SettlementWarning(SettlementWarningType.NO_SHARE_TARGET, EXPENSE_ID));
	}

	@Test
	@DisplayName("비참석자에게 걸린 조정은 결과에 영향이 없다")
	void ignoresAdjustmentsOfNonAttendees() {
		ExpenseDistribution fixedAbsent = distribute(90_000L, CHEOLSU, THREE, fixed(ABSENT, 30_000L));
		ExpenseDistribution excludedAbsent = distribute(90_000L, CHEOLSU, THREE, excluded(ABSENT));
		ExpenseDistribution weightedAbsent = distribute(90_000L, CHEOLSU, THREE, weight(ABSENT, 5));

		assertThat(List.of(fixedAbsent, excludedAbsent, weightedAbsent)).allSatisfy(result -> {
			assertThat(result.shares()).containsOnly(
					entry(CHEOLSU, 30_000L), entry(MINSU, 30_000L), entry(YOUNGHEE, 30_000L));
			assertThat(result.warnings()).isEmpty();
		});
	}
}
