/* 장소 탭 차수 카드의 데이터 영역(확정 장소·후보·득표·호스트 결정). HTML만 만들고 이벤트는 place.js가 단다 */

import {formatDate, memberName, safeLink} from '../../common.js';
import {html, raw} from '../../ui.js';

/**
 * 차수 장소 상태를 HTML로 만든다. 최상위 요소 하나로 감싼다(이벤트를 거기에 단다).
 * @param {{placeStatus: string, placeVoteDeadline: string|null, hostDecisionRequired: boolean,
 *   confirmedCandidateId: number|null, candidates: Array}} place 차수 장소 상태
 * @param {{candidates: Array<{candidateId: number, voteCount: number, voters: Array}>}|null} votes 득표(투표 중일 때만)
 * @param {{me: {id: number}, detail: {hostAuthority: boolean}}} ctx 모임 화면 상태
 * @returns {string} HTML
 */
export function roundData(place, votes, ctx) {
	if (place.placeStatus === 'CONFIRMED') {
		const confirmed = place.candidates.find((c) => c.id === place.confirmedCandidateId);
		return html`<div><p>📍 <strong>${confirmed?.label ?? '확정 장소'}</strong>
			${raw(confirmed ? mapLink(confirmed.mapUrl) : '')}</p>
			${raw(confirmed?.manualAddress ? html`<p class="muted">${confirmed.manualAddress}</p>` : '')}</div>`;
	}
	const header = place.placeStatus === 'VOTING'
		? html`<p>투표 중 · 마감 ${formatDate(place.placeVoteDeadline)}</p>`
		: html`<p class="muted">장소 미정</p>`;
	const decision = place.hostDecisionRequired
		? html`<p><mark>투표가 동점으로 끝났어요. 호스트가 정해 주세요.</mark></p>
			${raw(ctx.detail.hostAuthority ? html`<button type="button" class="secondary" data-act="roulette">동점 룰렛 돌리기</button>` : '')}`
		: '';
	const rows = place.candidates.map((c) => candidateRow(c, voteOf(votes, c.id), place, ctx));
	return html`<div>${raw(header)}${raw(decision)}
		${raw(rows.length ? html`<ul>${raw(rows.join(''))}</ul>` : html`<p class="muted">아직 후보가 없어요</p>`)}</div>`;
}

/**
 * 후보 한 줄: 이름·지도 링크·득표, 내 투표 버튼, 호스트 결정 대기면 확정 버튼.
 * @param {{id: number, label: string, manualAddress: string|null, mapUrl: string}} candidate 후보
 * @param {{voteCount: number, voters: Array}|null} vote 이 후보 득표
 * @param {{placeStatus: string, hostDecisionRequired: boolean}} place 차수 장소 상태
 * @param {{me: {id: number}, detail: {hostAuthority: boolean}}} ctx 모임 화면 상태
 * @returns {string} HTML
 */
function candidateRow(candidate, vote, place, ctx) {
	const voted = vote?.voters.some((member) => member.memberId === ctx.me.id);
	const voteButton = place.placeStatus === 'VOTING'
		? html`<button type="button" class="${voted ? '' : 'outline'}" data-act="${voted ? 'unvote' : 'vote'}" data-candidate="${candidate.id}">${voted ? '투표 취소' : '투표'}</button>`
		: '';
	const confirmButton = place.hostDecisionRequired && ctx.detail.hostAuthority
		? html`<button type="button" class="secondary outline" data-act="confirm" data-candidate="${candidate.id}">이걸로 확정</button>`
		: '';
	return html`<li><strong>${candidate.label}</strong> ${raw(mapLink(candidate.mapUrl))}
		${raw(vote ? html` — ${vote.voteCount}표 <small class="muted">${raw(vote.voters.map(memberName).join(', '))}</small>` : '')}
		${raw(candidate.manualAddress ? html`<br><small class="muted">${candidate.manualAddress}</small>` : '')}
		${raw(voteButton)}${raw(confirmButton)}</li>`;
}

/**
 * 후보 id의 득표를 찾는다.
 * @param {{candidates: Array}|null} votes 득표
 * @param {number} candidateId 후보 id
 * @returns {{voteCount: number, voters: Array}|null} 득표
 */
function voteOf(votes, candidateId) {
	return votes?.candidates.find((vote) => vote.candidateId === candidateId) ?? null;
}

/**
 * 지도 링크(서버가 https로 검증했거나 카카오맵 검색 링크로 만든 값). https가 아니면 그리지 않는다.
 * @param {string|null} url 지도 URL
 * @returns {string} HTML
 */
export function mapLink(url) {
	return safeLink(url, '지도');
}
