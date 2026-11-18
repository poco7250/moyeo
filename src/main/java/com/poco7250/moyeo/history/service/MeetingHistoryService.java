package com.poco7250.moyeo.history.service;

import com.poco7250.moyeo.boardgame.domain.BoardGameRank;
import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.boardgame.repository.BoardGameRecordRepository;
import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.repository.GameResultRepository;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.auth.GroupAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.history.dto.MeetingHistoryPageResponse;
import com.poco7250.moyeo.meeting.domain.Attendance;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingRepository;
import com.poco7250.moyeo.meeting.repository.RoundRepository;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.repository.PenaltyRepository;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.dto.MeetingExpenseTotal;
import com.poco7250.moyeo.settlement.repository.ExpenseRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 히스토리 조회(PRD H-1). 페이지의 모임 수와 상관없이 쿼리 수가 고정되도록 하위 데이터를 IN으로 한 번에 읽는다.
 * 읽기 전용이라 쓰기·실시간 이벤트가 없다.
 */
@Service
@RequiredArgsConstructor
public class MeetingHistoryService {

	// 최근 모임 순(같은 시각이면 나중에 만든 모임 먼저)
	private static final Sort RECENT_FIRST = Sort.by(Sort.Order.desc(Meeting::getStartAt),
			Sort.Order.desc(Meeting::getId));

	private final GroupAccessChecker groupAccessChecker;
	private final MeetingRepository meetingRepository;
	private final RoundRepository roundRepository;
	private final AttendanceRepository attendanceRepository;
	private final ExpenseRepository expenseRepository;
	private final SettlementRepository settlementRepository;
	private final GameSessionRepository gameSessionRepository;
	private final PenaltyRepository penaltyRepository;
	private final GameResultRepository gameResultRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final BoardGameRecordRepository boardGameRecordRepository;
	private final Clock clock;

	/**
	 * 그룹의 지난 모임(시작 시각이 지금 이전, 날짜 투표 중 제외) 한 페이지를 최근 순으로 돌려준다.
	 *
	 * @param page 0부터 시작하는 페이지 번호
	 * @throws BusinessException 그룹 멤버가 아니면(없는 그룹 포함) NOT_GROUP_MEMBER
	 */
	@Transactional(readOnly = true)
	public MeetingHistoryPageResponse list(Long groupId, Long memberId, int page, int size) {
		groupAccessChecker.requireMember(groupId, memberId);
		Slice<Meeting> meetings = meetingRepository.findAllByGroupIdAndStartAtLessThan(groupId,
				LocalDateTime.now(clock), PageRequest.of(page, size, RECENT_FIRST));
		if (meetings.isEmpty()) {
			return MeetingHistoryPageResponse.of(meetings, List.of());
		}
		MeetingHistorySource source = load(groupId, meetings.getContent());
		return MeetingHistoryPageResponse.of(meetings,
				new MeetingHistoryAssembler(source).assemble(meetings.getContent()));
	}

	private MeetingHistorySource load(Long groupId, List<Meeting> meetings) {
		List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
		List<Round> rounds = roundRepository.findAllWithConfirmedPlaceByMeetingIdIn(meetingIds);
		List<Long> roundIds = rounds.stream().map(Round::getId).toList();
		List<Attendance> attendances = attendanceRepository.findAllAttendingByRoundIdIn(roundIds);
		List<GameSession> games = MeetingHistoryAssembler.latestRevealedPerRoot(
				gameSessionRepository.findAllRevealedByRoundIdIn(roundIds));
		List<Penalty> penalties = games.isEmpty() ? List.of()
				: penaltyRepository.findAllActiveByGameSessionIdIn(games.stream().map(GameSession::getId).toList());
		List<GameResult> orderResults = orderResults(games);
		List<BoardGameRecord> boardGames = roundIds.isEmpty() ? List.of()
				: boardGameRecordRepository.findAllByRoundIdInOrderByIdAsc(roundIds);
		Stream<Long> memberIds = Stream.of(attendances.stream().map(a -> a.getMember().getId()),
				penalties.stream().map(p -> p.getMember().getId()),
				orderResults.stream().map(r -> r.getMember().getId()),
				boardGames.stream().flatMap(b -> b.getRanks().stream()).map(BoardGameRank::getMemberId))
				.flatMap(Function.identity());
		return new MeetingHistorySource(rounds, attendances, expenseTotals(meetingIds), settlements(meetingIds),
				games, penalties, orderResults, boardGames, groupMembers(groupId, memberIds));
	}

	private List<GameResult> orderResults(List<GameSession> games) {
		List<Long> orderGameIds = games.stream().filter(g -> g.getType().isOrder()).map(GameSession::getId).toList();
		return orderGameIds.isEmpty() ? List.of()
				: gameResultRepository.findAllByGameSessionIdInAndOutcome(orderGameIds, GameResult.OUTCOME_ORDERED);
	}

	private Map<Long, Long> expenseTotals(List<Long> meetingIds) {
		return expenseRepository.sumAmountByMeetingIdIn(meetingIds).stream()
				.collect(Collectors.toMap(MeetingExpenseTotal::meetingId, MeetingExpenseTotal::totalAmount));
	}

	private Map<Long, Settlement> settlements(List<Long> meetingIds) {
		return settlementRepository.findAllByMeetingIdIn(meetingIds).stream()
				.collect(Collectors.toMap(s -> s.getMeeting().getId(), Function.identity()));
	}

	/**
	 * 히스토리에 나오는 회원들의 그룹 멤버십을 한 번에 읽는다.
	 */
	private Map<Long, GroupMember> groupMembers(Long groupId, Stream<Long> memberIdStream) {
		Set<Long> memberIds = memberIdStream.collect(Collectors.toSet());
		if (memberIds.isEmpty()) {
			return Map.of();
		}
		return groupMemberRepository.findAllByGroupIdAndMemberIdIn(groupId, memberIds).stream()
				.collect(Collectors.toMap(gm -> gm.getMember().getId(), Function.identity()));
	}
}
