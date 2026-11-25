/* 지난 모임 히스토리(H-1): 최근 순 카드, 더 보기 */

import {api} from '../api.js';
import {findGroup, formatDate, formatWon, groupHeader, memberName, notMemberNotice} from '../common.js';
import {html, raw, render, showError} from '../ui.js';

const PAGE_SIZE = 10;
const SETTLEMENT_LABELS = {DRAFT: '정산 전', CONFIRMED: '정산 확정', REOPENED: '재확정 필요'};

/**
 * 히스토리 첫 페이지를 그리고 "더 보기"로 이어 붙인다.
 * @param {{params: {groupId: string}}} ctx 라우터 컨텍스트
 * @returns {Promise<void>}
 */
export async function groupHistoryView({params}) {
	const group = await findGroup(params.groupId);
	if (!group) {
		render(notMemberNotice());
		return;
	}
	const app = render(html`
		${raw(groupHeader(group, '지난 모임'))}
		<div id="history-list"></div>
		<button type="button" id="history-more" class="secondary" hidden>더 보기</button>
	`);
	let page = 0;
	const more = app.querySelector('#history-more');
	const load = async () => {
		const data = await api.get(`/api/groups/${group.id}/history?page=${page}&size=${PAGE_SIZE}`);
		appendCards(app.querySelector('#history-list'), data.items, page === 0);
		more.hidden = !data.hasNext;
		page += 1;
	};
	more.addEventListener('click', () => load().catch(showError));
	await load();
}

/**
 * 모임 카드를 목록 뒤에 붙인다.
 * @param {HTMLElement} list 목록 요소
 * @param {Array<object>} items 히스토리 모임
 * @param {boolean} first 첫 페이지인지(비었을 때 안내용)
 */
function appendCards(list, items, first) {
	if (first && items.length === 0) {
		list.innerHTML = html`<p class="muted">지난 모임이 없어요</p>`;
		return;
	}
	list.insertAdjacentHTML('beforeend', items.map(meetingCard).join(''));
}

/**
 * 지난 모임 카드 하나.
 * @param {{meetingId: number, title: string, startAt: string, attendeeCount: number, totalExpenseAmount: number,
 *   settlementStatus: string|null, settlementRevisionNo: number|null, rounds: Array<object>, games: Array<object>}} meeting 모임
 * @returns {string} HTML
 */
function meetingCard(meeting) {
	const settlement = meeting.settlementStatus
		? `${SETTLEMENT_LABELS[meeting.settlementStatus] ?? meeting.settlementStatus}`
			+ (meeting.settlementRevisionNo ? ` (리비전 ${meeting.settlementRevisionNo})` : '')
		: '정산 없음';
	return html`
		<article>
			<header><a href="/meetings/${meeting.meetingId}" data-link><strong>${meeting.title}</strong></a>
				<small class="muted">${formatDate(meeting.startAt)}</small></header>
			<p>참석 ${meeting.attendeeCount}명 · 총 지출 ${formatWon(meeting.totalExpenseAmount)} · ${settlement}</p>
			${raw(gameLine(meeting.games))}
			${raw(meeting.rounds.map(roundLine).join(''))}
		</article>
	`;
}

/**
 * 모임 게임 벌칙자 한 줄. 벌칙자가 없으면 비운다.
 * @param {Array<{penalized: Array}>} games 모임 게임
 * @returns {string} HTML
 */
function gameLine(games) {
	const penalized = games.flatMap((game) => game.penalized);
	return penalized.length ? html`<p>벌칙: ${raw(penalized.map(memberName).join(', '))}</p>` : '';
}

/**
 * 차수 한 줄: 장소·참석자·보드게임 꼴등.
 * @param {{name: string, place: {label: string}|null, attendees: Array, noShows: Array, boardGames: Array}} round 차수
 * @returns {string} HTML
 */
function roundLine(round) {
	const names = (members) => members.map(memberName).join(', ');
	const losers = round.boardGames.map((record) => record.loser).filter(Boolean);
	return html`
		<details>
			<summary>${round.name} · ${round.place?.label ?? '장소 미정'}</summary>
			<p>참석: ${raw(names(round.attendees) || '없음')}</p>
			${raw(round.noShows.length ? html`<p>노쇼: ${raw(names(round.noShows))}</p>` : '')}
			${raw(losers.length ? html`<p>보드게임 꼴등: ${raw(names(losers))}</p>` : '')}
		</details>
	`;
}
