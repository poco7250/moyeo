package com.poco7250.moyeo.game.service;

import com.poco7250.moyeo.game.domain.GameParticipant;
import com.poco7250.moyeo.game.domain.GameResult;
import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.game.domain.GameStatus;
import com.poco7250.moyeo.game.domain.PenaltyKind;
import com.poco7250.moyeo.game.dto.GameChangeSnapshot;
import com.poco7250.moyeo.game.dto.GameCreateRequest;
import com.poco7250.moyeo.game.dto.GameStateResponse;
import com.poco7250.moyeo.game.repository.GameParticipantRepository;
import com.poco7250.moyeo.game.repository.GameResultRepository;
import com.poco7250.moyeo.game.repository.GameSessionRepository;
import com.poco7250.moyeo.global.auth.GameAccessChecker;
import com.poco7250.moyeo.global.auth.MeetingAccessChecker;
import com.poco7250.moyeo.global.error.BusinessException;
import com.poco7250.moyeo.global.error.ErrorCode;
import com.poco7250.moyeo.meeting.domain.Round;
import com.poco7250.moyeo.meeting.repository.AttendanceRepository;
import com.poco7250.moyeo.meeting.repository.MeetingGuestRepository;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.member.repository.MemberRepository;
import com.poco7250.moyeo.penalty.service.PenaltyAssigner;
import com.poco7250.moyeo.settlement.domain.ChangeLogEntityType;
import com.poco7250.moyeo.settlement.domain.ChangeType;
import com.poco7250.moyeo.settlement.service.ChangeLogRecorder;
import com.poco7250.moyeo.settlement.service.SettlementGuard;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임 방의 생성·시작·종료·취소. 시작하면 그 자리에서 벌칙자를 뽑아 공개한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GameLobbyService {

	private final MeetingAccessChecker meetingAccessChecker;
	private final GameAccessChecker gameAccessChecker;
	private final GameSessionRepository gameSessionRepository;
	private final GameParticipantRepository gameParticipantRepository;
	private final MeetingGuestRepository meetingGuestRepository;
	private final MemberRepository memberRepository;
	private final GameResultRepository gameResultRepository;
	private final AttendanceRepository attendanceRepository;
	private final GameStateAssembler gameStateAssembler;
	private final GameSeedGenerator gameSeedGenerator;
	private final PenaltyAssigner penaltyAssigner;
	private final SettlementGuard settlementGuard;
	private final ChangeLogRecorder changeLogRecorder;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/**
	 * 고른 차수 참석자와 모임 게스트(G-6)로 게임 방을 만든다. 만든 회원이 진행자가 된다.
	 *
	 * @throws BusinessException 차수 참석자도 그 모임 게스트도 아닌 회원이 있으면 GAME_PARTICIPANT_NOT_ATTENDING
	 */
	public GameStateResponse create(Long roundId, Long memberId, GameCreateRequest request) {
		Round round = meetingAccessChecker.requireRoundMember(roundId, memberId);
		List<Member> members = findParticipants(round, request.participantMemberIds());
		GameSession game = gameSessionRepository.save(GameSession.create(round, request.type(), memberId,
				request.penaltyKind(), request.penaltyNote(), request.resolvedPenaltyCount()));
		game.markAsRoot();
		gameParticipantRepository.saveAll(members.stream().map(member -> GameParticipant.create(game, member)).toList());
		GameStateResponse state = gameStateAssembler.assemble(game);
		eventPublisher.publishEvent(GameStateChangedEvent.toMeeting(round.getMeeting().getId(), state));
		return state;
	}

	/**
	 * 게임을 시작해 벌칙자를 뽑고 바로 공개한다. 공개와 같은 트랜잭션에서 벌칙자를 확정한다.
	 *
	 * @throws BusinessException 진행자·호스트가 아니면 NOT_GAME_OPERATOR, WAITING이 아니면 GAME_INVALID_TRANSITION
	 */
	public GameStateResponse start(Long gameId, Long memberId) {
		GameSession game = gameAccessChecker.requireOperator(gameId, memberId);
		game.start();
		long seed = gameSeedGenerator.next();
		game.recordDraw(seed);
		game.reveal(LocalDateTime.now(clock));
		// 결과 insert의 FK 공유 잠금을 먼저 잡으면 동시 시작끼리 데드락이 나므로 version 갱신을 먼저 보낸다
		gameSessionRepository.flush();
		penaltyAssigner.onRevealed(game, memberId, saveResults(game, seed));
		return publishStateChanged(game);
	}

	/**
	 * 공개된 게임을 종료한다. 결과·정산에는 영향이 없다.
	 *
	 * @throws BusinessException 진행자·호스트가 아니면 NOT_GAME_OPERATOR, REVEALED가 아니면 GAME_INVALID_TRANSITION
	 */
	public GameStateResponse close(Long gameId, Long memberId) {
		GameSession game = gameAccessChecker.requireOperator(gameId, memberId);
		game.close();
		return publishStateChanged(game);
	}

	/**
	 * 공개 전 게임을 취소한다. 금액형이면 정산 확정 검사와 경합하지 않게 직렬화하고 변경 로그를 남긴다.
	 *
	 * @throws BusinessException 진행자·호스트가 아니면 NOT_GAME_OPERATOR, 공개 후면 GAME_NOT_CANCELABLE
	 */
	public GameStateResponse cancel(Long gameId, Long memberId) {
		GameSession game = gameAccessChecker.requireOperator(gameId, memberId);
		GameStatus before = game.getStatus();
		game.cancel();
		if (game.getPenaltyKind() == PenaltyKind.MONEY) {
			recordMoneyGameCancel(game, memberId, before);
		}
		return publishStateChanged(game);
	}

	/**
	 * 참가자 후보가 전부 차수 참석(ATTEND) 멤버이거나 그 모임 게스트인지 확인하고 회원을 돌려준다(참석자 먼저, 게스트 다음).
	 * 게스트는 참석 응답이 없어 두 집합이 겹치지 않는다.
	 */
	private List<Member> findParticipants(Round round, List<Long> memberIds) {
		List<Member> members = new ArrayList<>(attendanceRepository.findAttendingMembers(round.getId(), memberIds));
		List<Long> guestIds = meetingGuestRepository.findMemberIdsByMeetingIdAndMemberIdIn(
				round.getMeeting().getId(), memberIds);
		if (members.size() + guestIds.size() != memberIds.size()) {
			throw new BusinessException(ErrorCode.GAME_PARTICIPANT_NOT_ATTENDING);
		}
		guestIds.stream().sorted().map(memberRepository::getReferenceById).forEach(members::add);
		return members;
	}

	/**
	 * 추첨 결과를 참가자 전원에 대해 저장하고 벌칙자로 뽑힌 회원을 돌려준다. 순서 정하기 게임은 벌칙자가 없다.
	 */
	private List<Member> saveResults(GameSession game, long seed) {
		List<GameParticipant> participants = gameParticipantRepository.findAllByGameSessionIdOrderByIdAsc(game.getId());
		List<Long> memberIds = participants.stream().map(participant -> participant.getMember().getId()).toList();
		if (game.getType().isOrder()) {
			saveOrderResults(game, participants, GameDrawer.order(memberIds, seed));
			return List.of();
		}
		return savePenaltyResults(game, participants, GameDrawer.draw(memberIds, game.getPenaltyCount(), seed));
	}

	/**
	 * 정해진 순서대로 참가자마다 1번부터 순번 결과를 저장한다.
	 */
	private void saveOrderResults(GameSession game, List<GameParticipant> participants, List<Long> order) {
		Map<Long, Member> membersById = participants.stream()
				.map(GameParticipant::getMember)
				.collect(Collectors.toMap(Member::getId, Function.identity()));
		gameResultRepository.saveAll(IntStream.range(0, order.size())
				.mapToObj(i -> GameResult.ordered(game, membersById.get(order.get(i)), i + 1))
				.toList());
	}

	/**
	 * 참가자마다 꽝·통과 결과를 저장하고 벌칙자로 뽑힌 회원을 돌려준다.
	 */
	private List<Member> savePenaltyResults(GameSession game, List<GameParticipant> participants, Set<Long> penalized) {
		gameResultRepository.saveAll(participants.stream()
				.map(participant -> GameResult.create(game, participant.getMember(),
						penalized.contains(participant.getMember().getId())
								? GameResult.OUTCOME_PENALTY : GameResult.OUTCOME_SAFE))
				.toList());
		return participants.stream()
				.map(GameParticipant::getMember)
				.filter(member -> penalized.contains(member.getId()))
				.toList();
	}

	private void recordMoneyGameCancel(GameSession game, Long memberId, GameStatus before) {
		Long meetingId = game.getRound().getMeeting().getId();
		settlementGuard.lockOnly(meetingId);
		changeLogRecorder.record(meetingId, memberId, ChangeLogEntityType.GAME_SESSION, game.getId(), ChangeType.UPDATE,
				GameChangeSnapshot.of(game, before), GameChangeSnapshot.of(game, game.getStatus()));
	}

	private GameStateResponse publishStateChanged(GameSession game) {
		GameStateResponse state = gameStateAssembler.assemble(game);
		eventPublisher.publishEvent(GameStateChangedEvent.toGame(game.getId(), state));
		eventPublisher.publishEvent(GameStateChangedEvent.toMeeting(game.getRound().getMeeting().getId(), state));
		return state;
	}
}
