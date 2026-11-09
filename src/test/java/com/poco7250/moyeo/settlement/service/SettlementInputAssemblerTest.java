package com.poco7250.moyeo.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.poco7250.moyeo.group.domain.Group;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.AttendanceStatus;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.settlement.calc.ExpenseInput;
import com.poco7250.moyeo.settlement.calc.SettlementInput;
import com.poco7250.moyeo.settlement.calc.ShareAdjustment;
import com.poco7250.moyeo.settlement.domain.Expense;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementInputAssemblerTest {

	private static final Long MEETING_ID = 2L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 18, 0);

	@Mock
	ExpenseRepository expenseRepository;

	@Mock
	AttendanceRepository attendanceRepository;

	@InjectMocks
	SettlementInputAssembler assembler;

	private Round firstRound;
	private Round secondRound;
	private Member chulsoo;
	private Member jisoo;
	private Member minsoo;

	@BeforeEach
	void setUp() {
		Meeting meeting = Meeting.create(Group.create("대학 동기"), "연말 모임", NOW, null, null, 1L);
		firstRound = round(meeting, 1, 21L);
		secondRound = round(meeting, 2, 22L);
		chulsoo = member(1L);
		jisoo = member(2L);
		minsoo = member(3L);
	}

	@Test
	@DisplayName("분배 대상은 지출이 속한 차수의 ATTEND 응답자뿐이다")
	void onlyAttendeesOfRound() {
		Expense first = expense(31L, firstRound, chulsoo, 90_000L);
		Expense second = expense(32L, secondRound, jisoo, 40_000L);
		given(expenseRepository.findAllWithSharesByMeetingId(MEETING_ID)).willReturn(List.of(first, second));
		given(attendanceRepository.findAllByMeetingId(MEETING_ID)).willReturn(List.of(
				attendance(firstRound, chulsoo, AttendanceStatus.ATTEND),
				attendance(firstRound, jisoo, AttendanceStatus.ATTEND),
				attendance(firstRound, minsoo, AttendanceStatus.ABSENT),
				attendance(secondRound, jisoo, AttendanceStatus.ATTEND),
				attendance(secondRound, chulsoo, AttendanceStatus.UNDECIDED)));

		SettlementInput input = assembler.assemble(MEETING_ID);

		assertThat(input.expenses()).containsExactly(
				new ExpenseInput(31L, 90_000L, 1L, Set.of(1L, 2L), List.of()),
				new ExpenseInput(32L, 40_000L, 2L, Set.of(2L), List.of()));
	}

	@Test
	@DisplayName("참석 응답이 없는 차수의 지출은 분배 대상이 비어 있다")
	void noAttendance() {
		given(expenseRepository.findAllWithSharesByMeetingId(MEETING_ID))
				.willReturn(List.of(expense(31L, firstRound, chulsoo, 10_000L)));
		given(attendanceRepository.findAllByMeetingId(MEETING_ID)).willReturn(List.of());

		assertThat(assembler.assemble(MEETING_ID).expenses()).singleElement()
				.satisfies(expense -> assertThat(expense.attendeeIds()).isEmpty());
	}

	@Test
	@DisplayName("분배 조정을 제외·고정 금액·가중치 입력으로 변환한다")
	void convertsAdjustments() {
		Expense expense = expense(31L, firstRound, chulsoo, 90_000L);
		expense.addShare(chulsoo, null, 2, false);
		expense.addShare(jisoo, null, null, true);
		expense.addShare(minsoo, 5_000L, null, false);
		given(expenseRepository.findAllWithSharesByMeetingId(MEETING_ID)).willReturn(List.of(expense));
		given(attendanceRepository.findAllByMeetingId(MEETING_ID)).willReturn(List.of());

		assertThat(assembler.assemble(MEETING_ID).expenses().getFirst().adjustments()).containsExactly(
				new ShareAdjustment(1L, false, null, 2),
				new ShareAdjustment(2L, true, null, null),
				new ShareAdjustment(3L, false, 5_000L, null));
	}

	private Round round(Meeting meeting, int seq, Long id) {
		Round round = Round.create(meeting, seq, seq + "차");
		ReflectionTestUtils.setField(round, "id", id);
		return round;
	}

	private Member member(Long id) {
		Member member = Member.ofKakao(id, "회원" + id, null, null);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}

	private Expense expense(Long id, Round round, Member payer, long amount) {
		Expense expense = Expense.create(round, payer, amount, null);
		ReflectionTestUtils.setField(expense, "id", id);
		return expense;
	}

	private Attendance attendance(Round round, Member member, AttendanceStatus status) {
		return Attendance.respond(round, member, status, NOW);
	}
}
