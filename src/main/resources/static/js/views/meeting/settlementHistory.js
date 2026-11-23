/*
 * 정산 이력(S-11, 접어 두고 열 때만 불러옴): 리비전 목록과 변경 로그.
 * 모임 결산 카드(H-3): 지난 모임이면 정산 탭 맨 아래에 카드로 보여 준다(이미지 저장은 하지 않음).
 */

import {ApiError, api} from '../../api.js';
import {formatDate, formatWon, memberName, nameOf, runAction} from '../../common.js';
import {html, raw} from '../../ui.js';

const LOG_PAGE_SIZE = 20;
const ENTITY_LABELS = {EXPENSE: '지출', EXPENSE_SHARE: '지출 나누기', ATTENDANCE: '참석', GAME_SESSION: '게임',
	PENALTY_RULE: '벌칙 조건', PENALTY: '벌칙'};
const CHANGE_LABELS = {CREATE: '추가', UPDATE: '변경', DELETE: '삭제'};

/**
 * 정산 이력 영역을 만든다. 열 때마다 새로 받는다.
 * @param {HTMLElement} box 이력 영역
 * @param {{ctx: object, directory: Map}} state 정산 탭 상태
 */
export function mountHistory(box, state) {
	box.innerHTML = html`<details class="history-area"><summary>정산 이력(리비전·변경 로그)</summary>
		<div class="revisions"></div><h4>변경 로그</h4><ul class="change-logs"></ul>
		<button type="button" class="secondary logs-more" hidden>더 보기</button></details>`;
	const area = box.querySelector('.history-area');
	const logs = {page: 0};
	area.addEventListener('toggle', () => {
		if (area.open) {
			runAction(new Event('toggle'), () => loadHistory(area, state, logs));
		}
	});
	area.querySelector('.logs-more').addEventListener('click', (event) => runAction(event, () => loadLogs(area, state, logs)));
}

/**
 * 리비전과 변경 로그 첫 페이지를 받는다.
 * @param {HTMLElement} area 이력 영역
 * @param {{ctx: object, directory: Map}} state 정산 탭 상태
 * @param {{page: number}} logs 변경 로그 페이지 상태
 * @returns {Promise<void>}
 */
async function loadHistory(area, state, logs) {
	const revisions = await api.get(`/api/meetings/${state.ctx.meetingId}/settlement/revisions`);
	area.querySelector('.revisions').innerHTML = revisions.length
		? revisions.map((revision) => revisionCard(revision, state.directory)).join('')
		: html`<p class="muted">아직 확정한 적이 없어요</p>`;
	logs.page = 0;
	area.querySelector('.change-logs').innerHTML = '';
	await loadLogs(area, state, logs);
}

/**
 * 리비전 카드: 확정자·시각, 멤버별 부담액·순잔액·변동액, 송금 안내, 벌칙 부담.
 * @param {{revisionNo: number, confirmedBy: number, confirmedAt: string, members: Array, changes: Array,
 *   transfers: Array, penaltyCharges: Array}} revision 리비전
 * @param {Map<number, object>} directory 이름표
 * @returns {string} HTML
 */
function revisionCard(revision, directory) {
	const delta = new Map(revision.changes.map((c) => [c.memberId, c.delta]));
	const members = revision.members.map((m) => html`<li>${raw(nameOf(directory, m.memberId))} 부담 ${formatWon(m.shareAmount)}
		· 순잔액 ${formatWon(m.netBalance)}${delta.get(m.memberId) ? ` (변동 ${formatWon(delta.get(m.memberId))})` : ''}</li>`);
	const transfers = revision.transfers.map((t) => html`<li>${raw(nameOf(directory, t.fromMemberId))} → ${raw(nameOf(directory, t.toMemberId))} ${formatWon(t.amount)}</li>`);
	const charges = revision.penaltyCharges.map((c) => html`<li>${raw(nameOf(directory, c.memberId))} 벌칙 ${formatWon(c.amount)}</li>`);
	return html`<article><header>리비전 ${revision.revisionNo} · ${raw(nameOf(directory, revision.confirmedBy))} · ${formatDate(revision.confirmedAt)}</header>
		<ul>${raw(members.join(''))}</ul>
		<p><small>송금 안내</small></p><ul>${raw(transfers.join('') || html`<li>없음</li>`)}</ul>
		${raw(charges.length ? html`<p><small>벌칙 부담</small></p><ul>${raw(charges.join(''))}</ul>` : '')}</article>`;
}

/**
 * 변경 로그 한 페이지를 이어 붙인다.
 * @param {HTMLElement} area 이력 영역
 * @param {{ctx: object, directory: Map}} state 정산 탭 상태
 * @param {{page: number}} logs 변경 로그 페이지 상태
 * @returns {Promise<void>}
 */
async function loadLogs(area, state, logs) {
	const data = await api.get(`/api/meetings/${state.ctx.meetingId}/change-logs?page=${logs.page}&size=${LOG_PAGE_SIZE}`);
	const items = data.items.map((log) => html`<li><small>${formatDate(log.changedAt)}</small>
		${raw(nameOf(state.directory, log.actorId))} · ${ENTITY_LABELS[log.entityType] ?? log.entityType} ${CHANGE_LABELS[log.changeType] ?? log.changeType}
		<details><summary>전/후</summary><pre>${JSON.stringify(log.before, null, 1)}\n→ ${JSON.stringify(log.after, null, 1)}</pre></details></li>`);
	area.querySelector('.change-logs').insertAdjacentHTML('beforeend',
		items.join('') || (logs.page === 0 ? html`<li class="muted">변경 로그가 없어요</li>` : ''));
	area.querySelector('.logs-more').hidden = !data.hasNext;
	logs.page += 1;
}

/**
 * 모임 결산 카드를 그린다. 지난 모임이 아니라 서버가 거절하면(4xx) 숨긴다.
 * @param {HTMLElement} box 카드 영역
 * @param {{meetingId: string}} ctx 모임 화면 상태
 * @returns {Promise<void>}
 */
export async function mountMeetingCard(box, ctx) {
	let card;
	try {
		card = await api.get(`/api/meetings/${ctx.meetingId}/summary-card`);
	} catch (error) {
		if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
			box.innerHTML = '';
			return;
		}
		throw error;
	}
	const {meeting} = card;
	const shares = card.shares.map((s) => html`<li>${raw(memberName(s.member))} ${formatWon(s.shareAmount)}</li>`);
	const places = meeting.rounds.map((r) => html`<li>${r.name}: ${r.place?.label ?? '장소 미정'}</li>`);
	box.innerHTML = html`<article class="meeting-card"><header><small>${card.groupName}</small>
		<h3>${meeting.title}</h3><small>${formatDate(meeting.startAt)} · 참석 ${meeting.attendeeCount}명 · 총 ${formatWon(meeting.totalExpenseAmount)}</small></header>
		<ul>${raw(places.join(''))}</ul>
		${raw(shares.length ? html`<p>1인당 부담(마지막 확정 기준)</p><ul>${raw(shares.join(''))}</ul>` : '')}</article>`;
}
