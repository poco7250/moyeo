package com.poco7250.moyeo.settlement.service;

import com.poco7250.moyeo.group.domain.GroupMember;
import com.poco7250.moyeo.group.repository.GroupMemberRepository;
import com.poco7250.moyeo.settlement.calc.MemberSettlement;
import com.poco7250.moyeo.settlement.calc.NetBalanceDelta;
import com.poco7250.moyeo.settlement.calc.NetBalanceDeltaCalculator;
import com.poco7250.moyeo.settlement.calc.SettlementResult;
import com.poco7250.moyeo.settlement.calc.ShareTextBuilder;
import com.poco7250.moyeo.settlement.domain.RevisionMemberShare;
import com.poco7250.moyeo.settlement.domain.Settlement;
import com.poco7250.moyeo.settlement.domain.SettlementRevision;
import com.poco7250.moyeo.settlement.dto.RevisionChangeResponse;
import com.poco7250.moyeo.settlement.repository.RevisionMemberShareRepository;
import com.poco7250.moyeo.settlement.repository.SettlementRevisionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 재확정 시 직전 확정 리비전 대비 변동액과 공유 문구를 조립한다(PRD S-11). 변동액은 저장하지 않는다.
 */
@Component
@RequiredArgsConstructor
class RevisionChangeAssembler {

	private final SettlementRevisionRepository revisionRepository;
	private final RevisionMemberShareRepository memberShareRepository;
	private final GroupMemberRepository groupMemberRepository;

	/**
	 * 직전 리비전(revisionNo − 1) 순잔액과 이번 계산 순잔액으로 변동액 안내를 만든다. 최초 확정이면 null이다.
	 *
	 * @param groupId 공유 문구에 쓸 닉네임을 찾을 그룹
	 * @throws IllegalStateException 재확정인데 직전 리비전이 없으면
	 */
	RevisionChangeResponse assemble(Settlement settlement, Long groupId, int revisionNo, SettlementResult result) {
		if (revisionNo == Settlement.FIRST_REVISION_NO) {
			return null;
		}
		int previousNo = revisionNo - 1;
		SettlementRevision previous = revisionRepository.findBySettlementIdAndRevisionNo(settlement.getId(), previousNo)
				.orElseThrow(() -> new IllegalStateException(
						"재확정할 정산에 직전 리비전이 없습니다: " + settlement.getId() + "#" + previousNo));
		Map<Long, Long> previousBalances = memberShareRepository
				.findAllByRevisionIdOrderByMemberIdAsc(previous.getId()).stream()
				.collect(Collectors.toMap(share -> share.getMember().getId(), RevisionMemberShare::getNetBalance));
		Map<Long, Long> currentBalances = result.members().stream()
				.collect(Collectors.toMap(MemberSettlement::memberId, MemberSettlement::netBalance));
		List<NetBalanceDelta> deltas = NetBalanceDeltaCalculator.calculate(previousBalances, currentBalances);
		String shareText = ShareTextBuilder.build(revisionNo, deltas, nicknames(groupId, deltas),
				RevisionChangeResponse.NOTICE);
		return RevisionChangeResponse.of(previousNo, deltas, shareText);
	}

	private Map<Long, String> nicknames(Long groupId, List<NetBalanceDelta> deltas) {
		List<Long> memberIds = deltas.stream().map(NetBalanceDelta::memberId).toList();
		return groupMemberRepository.findAllByGroupIdAndMemberIdIn(groupId, memberIds).stream()
				.collect(Collectors.toMap(groupMember -> groupMember.getMember().getId(), GroupMember::getNickname));
	}
}
