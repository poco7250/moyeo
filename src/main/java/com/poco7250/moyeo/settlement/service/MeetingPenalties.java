package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.game.domain.GameSession;
import com.poco7250.moyeo.member.domain.Member;
import com.poco7250.moyeo.penalty.domain.Penalty;
import com.poco7250.moyeo.penalty.domain.PenaltyStatus;
import com.poco7250.moyeo.settlement.calc.GamePenaltyInput;
import com.poco7250.moyeo.settlement.calc.PenalizedMember;
import com.poco7250.moyeo.settlement.calc.PenaltyChargeResult;
import com.poco7250.moyeo.settlement.dto.SettlementPenaltyResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 모임의 금액형·비무효 벌칙과 그 게임들의 참가자 묶음. 정산 계산 입력과 벌칙 반영 내역이 같은 값을 쓴다.
 *
 * @param penalties           게임 순번 → 원 회차 id → 벌칙 id 순 벌칙(게스트 벌칙 포함)
 * @param participantsByGameId 게임 id별 참가자 회원
 */
public record MeetingPenalties(List<Penalty> penalties, Map<Long, List<Member>> participantsByGameId) {

	/**
	 * 벌칙이 없는 묶음.
	 */
	public static MeetingPenalties empty() {
		return new MeetingPenalties(List.of(), Map.of());
	}

	/**
	 * 금액이 정해진 게임마다 계산 입력을 만든다(게임 순번 순). 정산에 반영하는 건 그룹 멤버의 APPLIED 벌칙뿐이다.
	 */
	public List<GamePenaltyInput> toGameInputs() {
		return byGame().values().stream()
				.filter(gamePenalties -> gamePenalties.getFirst().getGameSession().chargesMoney())
				.map(this::toGameInput)
				.toList();
	}

	/**
	 * 계산된 벌칙 부담액과 나눠 받은 사람을 붙여 벌칙 반영 내역(PRD S-4)을 만든다. 순서는 벌칙 조회 순서(게임 순번 순)다.
	 */
	public List<SettlementPenaltyResponse> toResponses(List<PenaltyChargeResult> charges) {
		Map<Long, Long> chargeByPenaltyId = charges.stream()
				.collect(Collectors.toMap(PenaltyChargeResult::penaltyId, PenaltyChargeResult::amount, Long::sum));
		Map<Long, Set<Long>> creditedByGameId = new LinkedHashMap<>();
		byGame().forEach((gameId, gamePenalties) -> creditedByGameId.put(gameId, creditedMemberIds(gamePenalties)));
		return penalties.stream()
				.map(penalty -> SettlementPenaltyResponse.of(penalty, chargeByPenaltyId.get(penalty.getId()),
						creditedByGameId.get(penalty.getGameSession().getId())))
				.toList();
	}

	private Map<Long, List<Penalty>> byGame() {
		return penalties.stream().collect(Collectors.groupingBy(penalty -> penalty.getGameSession().getId(),
				LinkedHashMap::new, Collectors.toList()));
	}

	private GamePenaltyInput toGameInput(List<Penalty> gamePenalties) {
		GameSession game = gamePenalties.getFirst().getGameSession();
		List<PenalizedMember> penalized = gamePenalties.stream()
				.filter(penalty -> penalty.getStatus() == PenaltyStatus.APPLIED && !penalty.getMember().isGuest())
				.map(penalty -> new PenalizedMember(penalty.getId(), penalty.getMember().getId()))
				.toList();
		return new GamePenaltyInput(game.getId(), game.getPenaltyAmount(), penalized, creditedMemberIds(gamePenalties));
	}

	/**
	 * 나눠 받는 사람 = 게임 참가자 − 그 게임의 비무효 벌칙자(면제 포함) − 게스트, memberId 오름차순.
	 */
	private Set<Long> creditedMemberIds(List<Penalty> gamePenalties) {
		Long gameId = gamePenalties.getFirst().getGameSession().getId();
		Set<Long> penalizedIds = gamePenalties.stream()
				.map(penalty -> penalty.getMember().getId())
				.collect(Collectors.toSet());
		return participantsByGameId.getOrDefault(gameId, List.of()).stream()
				.filter(member -> !member.isGuest() && !penalizedIds.contains(member.getId()))
				.map(Member::getId)
				.collect(Collectors.toCollection(TreeSet::new));
	}
}
