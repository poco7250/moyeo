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
		return html`<div class="highlight"><small>📍 확정 장소</small>
			<span class="big">${confirmed?.label ?? '확정 장소'}</span>
			${raw(confirmed?.manualAddress ? html`<small>${confirmed.manualAddress}</small> ` : '')}
			${raw(confirmed ? mapChip(confirmed.mapUrl) : '')}</div>`;
	}
	const header = place.placeStatus === 'VOTING'
		? html`<p><span class="chip primary">투표 중</span> <small class="muted">마감 ${formatDate(place.placeVoteDeadline)}</small></p>`
		: html`<p><span class="chip">장소 미정</span></p>`;
	const decision = place.hostDecisionRequired
		? html`<p><span class="chip warn">투표가 동점으로 끝났어요. 호스트가 정해 주세요.</span></p>
			${raw(ctx.detail.hostAuthority ? html`<button type="button" class="secondary block-button" data-act="roulette">🎡 동점 룰렛 돌리기</button>` : '')}`
		: '';
	const maxVotes = Math.max(1, ...(votes?.candidates ?? []).map((v) => v.voteCount));
	const rows = place.candidates.map((c) => candidateRow(c, voteOf(votes, c.id), place, ctx, maxVotes));
	return html`<div>${raw(header)}${raw(decision)}
		${raw(rows.length ? html`<ul class="list">${raw(rows.join(''))}</ul>` : html`<p class="empty">아직 후보가 없어요</p>`)}</div>`;
}

/**
 * 후보 한 줄: 이름·주소·지도 링크, 득표 막대·투표한 사람, 내 투표 버튼, 호스트 결정 대기면 확정 버튼.
 * @param {{id: number, label: string, manualAddress: string|null, mapUrl: string}} candidate 후보
 * @param {{voteCount: number, voters: Array}|null} vote 이 후보 득표
 * @param {{placeStatus: string, hostDecisionRequired: boolean}} place 차수 장소 상태
 * @param {{me: {id: number}, detail: {hostAuthority: boolean}}} ctx 모임 화면 상태
 * @param {number} maxVotes 가장 많은 득표(막대 길이 기준)
 * @returns {string} HTML
 */
function candidateRow(candidate, vote, place, ctx, maxVotes) {
	const voted = vote?.voters.some((member) => member.memberId === ctx.me.id);
	const voteButton = place.placeStatus === 'VOTING'
		? html`<button type="button" class="small ${voted ? '' : 'secondary'}" data-act="${voted ? 'unvote' : 'vote'}" data-candidate="${candidate.id}">${voted ? '✓ 투표함' : '투표'}</button>`
		: '';
	const confirmButton = place.hostDecisionRequired && ctx.detail.hostAuthority
		? html`<button type="button" class="small secondary" data-act="confirm" data-candidate="${candidate.id}">이걸로 확정</button>`
		: '';
	const voteInfo = vote ? html`<div class="vote-bar"><i style="width:${Math.round((vote.voteCount / maxVotes) * 100)}%"></i></div>
		<span class="row-sub">${vote.voteCount}표 ${raw(vote.voters.map(memberName).join(', '))}</span>` : '';
	return html`<li><div class="list-row">
		<span class="row-body">
			<span class="row-title">${candidate.label} ${raw(mapChip(candidate.mapUrl))}</span>
			${raw(candidate.manualAddress ? html`<span class="row-sub">${candidate.manualAddress}</span>` : '')}
			${raw(voteInfo)}
		</span>
		${raw(voteButton)}${raw(confirmButton)}
	</div></li>`;
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
 * 지도 링크를 작은 칩으로 감싼다(링크가 없으면 빈 문자열).
 * @param {string|null} url 지도 URL
 * @returns {string} HTML
 */
function mapChip(url) {
	const link = mapLink(url);
	return link ? html`<span class="chip">${raw(link)}</span>` : '';
}

/**
 * 지도 링크(서버가 https로 검증했거나 카카오맵 검색 링크로 만든 값). https가 아니면 그리지 않는다.
 * @param {string|null} url 지도 URL
 * @returns {string} HTML
 */
export function mapLink(url) {
	return safeLink(url, '지도');
}
