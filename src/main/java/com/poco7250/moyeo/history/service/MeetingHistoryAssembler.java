package com.poco7250.moyeo.history.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.poco7250.moyeo.boardgame.domain.BoardGameRecord;
import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.history.dto.BoardGameHistoryResponse;
import com.poco7250.moyeo.history.dto.BoardGameRankHistoryResponse;
import com.poco7250.moyeo.history.dto.GameHistoryResponse;
import com.poco7250.moyeo.history.dto.HistoryMemberResponse;
import com.poco7250.moyeo.history.dto.MeetingHistoryResponse;
import com.poco7250.moyeo.history.dto.RoundHistoryResponse;
import com.poco7250.moyeo.meeting.domain.Meeting;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.dto.ConfirmedPlaceResponse;
import com.poco7250.moyeo.settlement.domain.Settlement;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * 미리 읽어 둔 데이터로 모임 히스토리 응답을 조립한다. 조회는 하지 않는다.
 */
final class MeetingHistoryAssembler {

	private final MeetingHistorySource source;
	private final Map<Long, List<Round>> roundsByMeeting;
	private final Map<Long, List<Long>> attendeesByRound;
	private final Map<Long, List<GameSession>> gamesByRound;
	private final Map<Long, List<Long>> penalizedByGame;
	private final Map<Long, List<Long>> orderedByGame;
	private final Map<Long, List<BoardGameRecord>> boardGamesByRound;

	MeetingHistoryAssembler(MeetingHistorySource source) {
		this.source = source;
		this.roundsByMeeting = source.rounds().stream().collect(groupingBy(r -> r.getMeeting().getId()));
		this.attendeesByRound = source.attendances().stream()
				.collect(groupingBy(a -> a.getRound().getId(), mapping(a -> a.getMember().getId(), toList())));
		this.gamesByRound = source.games().stream().collect(groupingBy(g -> g.getRound().getId()));
		this.penalizedByGame = source.penalties().stream()
				.collect(groupingBy(p -> p.getGameSession().getId(), mapping(p -> p.getMember().getId(), toList())));
		this.orderedByGame = source.orderResults().stream()
				.sorted(Comparator.comparing(GameResult::getOrderNo))
				.collect(groupingBy(r -> r.getGameSession().getId(), mapping(r -> r.getMember().getId(), toList())));
		this.boardGamesByRound = source.boardGames().stream().collect(groupingBy(b -> b.getRound().getId()));
	}

	/**
	 * 결과 공개된 게임 중 원 회차(rootSessionId)마다 마지막 회차만 남겨 원 회차 순으로 돌려준다.
	 * 다시 돌린 회차가 공개되면 원 회차 벌칙은 무효가 되므로 마지막 공개 회차가 실제 결과다.
	 */
	static List<GameSession> latestRevealedPerRoot(Collection<GameSession> revealed) {
		BinaryOperator<GameSession> later = (a, b) -> a.getId() > b.getId() ? a : b;
		return revealed.stream()
				.collect(Collectors.toMap(MeetingHistoryAssembler::rootOf, g -> g, later))
				.values().stream()
				.sorted(Comparator.comparing(MeetingHistoryAssembler::rootOf))
				.toList();
	}

	/**
	 * 모임 목록 순서대로 히스토리 응답을 만든다.
	 */
	List<MeetingHistoryResponse> assemble(List<Meeting> meetings) {
		return meetings.stream().map(this::meeting).toList();
	}

	private MeetingHistoryResponse meeting(Meeting meeting) {
		List<Round> rounds = roundsByMeeting.getOrDefault(meeting.getId(), List.of());
		Set<Long> attendees = rounds.stream()
				.flatMap(r -> attendeesByRound.getOrDefault(r.getId(), List.of()).stream())
				.collect(Collectors.toSet());
		Settlement settlement = source.settlements().get(meeting.getId());
		return new MeetingHistoryResponse(meeting.getId(), meeting.getTitle(), meeting.getStartAt(),
				attendees.size(), source.expenseTotals().getOrDefault(meeting.getId(), 0L),
				settlement == null ? null : settlement.getStatus(),
				settlement == null ? null : settlement.getCurrentRevisionNo(),
				rounds.stream().sorted(Comparator.comparingInt(Round::getSeq)).map(this::round).toList());
	}

	private RoundHistoryResponse round(Round round) {
		List<GameHistoryResponse> games = gamesByRound.getOrDefault(round.getId(), List.of()).stream()
				.map(this::game).toList();
		return new RoundHistoryResponse(round.getId(), round.getSeq(), round.getName(),
				ConfirmedPlaceResponse.from(round.confirmedPlace()),
				members(attendeesByRound.getOrDefault(round.getId(), List.of())), games,
				boardGamesByRound.getOrDefault(round.getId(), List.of()).stream().map(this::boardGame).toList());
	}

	private BoardGameHistoryResponse boardGame(BoardGameRecord record) {
		return new BoardGameHistoryResponse(record.getId(), record.getGameName(), record.getRanks().stream()
				.map(rank -> new BoardGameRankHistoryResponse(rank.getRankNo(),
						HistoryMemberResponse.of(rank.getMemberId(), source.groupMembers().get(rank.getMemberId()))))
				.toList());
	}

	private GameHistoryResponse game(GameSession game) {
		return new GameHistoryResponse(game.getId(), game.getType(), game.getPenaltyKind(), game.getPenaltyNote(),
				game.getRevealedAt(), members(penalizedByGame.getOrDefault(game.getId(), List.of())),
				orderedMembers(orderedByGame.getOrDefault(game.getId(), List.of())));
	}

	/**
	 * 순번 순서를 그대로 두고 응답으로 바꾼다.
	 */
	private List<HistoryMemberResponse> orderedMembers(List<Long> memberIds) {
		return memberIds.stream().map(id -> HistoryMemberResponse.of(id, source.groupMembers().get(id))).toList();
	}

	/**
	 * 회원 id를 그룹 가입 순(멤버십 id 순)으로 정렬해 응답으로 바꾼다. 멤버십이 없으면 맨 뒤로 보낸다.
	 */
	private List<HistoryMemberResponse> members(List<Long> memberIds) {
		Map<Long, GroupMember> groupMembers = source.groupMembers();
		Comparator<Long> joinOrder = Comparator.comparing(id -> groupMembers.containsKey(id)
				? groupMembers.get(id).getId() : Long.MAX_VALUE);
		return memberIds.stream().distinct().sorted(joinOrder.thenComparing(Comparator.naturalOrder()))
				.map(id -> HistoryMemberResponse.of(id, groupMembers.get(id))).toList();
	}

	private static Long rootOf(GameSession game) {
		return game.getRootSessionId() == null ? game.getId() : game.getRootSessionId();
	}
}
