/*
 * 게임 탭(R-0~R-5, R-8, R-10). 차수별 게임 카드(데이터, 실시간으로 다시 그림)와 게임 만들기 폼(한 번만 그림),
 * 보드게임 기록(R-7)을 둔다. 결과 연출은 게임 실행 화면(/meetings/{id}/games/{gameId})에서 본다.
 */

import {api} from '../../api.js';
import {memberDirectory, nameOf, runAction} from '../../common.js';
import {html, raw, toast} from '../../ui.js';
import {mountBoardGames} from './boardGame.js';
import {ACTION_LABELS, GAME_STATUS, GAME_TYPES, PENALTY_KINDS, PENALTY_STATUS, availableActions, canOperate,
	runGameAction} from './gameCommon.js';

const GAME_ICONS = {LADDER: '🪜', DRAW: '🎟️', ROULETTE: '🎡', ORDER: '🔢'};
const STATUS_TONES = {WAITING: '', PLAYING: 'primary', REVEALED: 'warn', CLOSED: 'success', CANCELED: ''};
const EVENTS = new Set(['GAME_STATE_CHANGED', 'BOARD_GAME_RECORD_CHANGED', 'BOARD_GAME_RECORD_DELETED',
	'PENALTY_CONDITION_RESET', 'SETTLEMENT_REOPENED', 'SETTLEMENT_RECONFIRMED']);

/**
 * 게임 탭을 그린다.
 * @param {HTMLElement} container 탭 본문
 * @param {{meetingId: string, me: {id: number}, detail: object}} ctx 모임 화면 상태
 * @returns {Promise<{events: Set<string>, refresh: Function}>} 실시간 갱신 핸들
 */
export async function mountGameTab(container, ctx) {
	container.innerHTML = html`<div class="game-tab">
		<section class="game-list"></section>
		<section class="game-create"></section>
		<section class="board-games"></section>
	</div>`;
	const tab = container.querySelector('.game-tab');
	const state = {ctx, directory: await memberDirectory(ctx.detail)};
	const board = await api.get(`/api/meetings/${ctx.meetingId}/attendance-board`);
	const refreshList = () => renderGameList(tab.querySelector('.game-list'), state, refreshList);
	await refreshList();
	renderCreateForm(tab.querySelector('.game-create'), board, state, refreshList);
	const boardGames = await mountBoardGames(tab.querySelector('.board-games'), board, state);
	return {
		events: EVENTS,
		refresh: async () => {
			await refreshList();
			await boardGames.refresh();
		},
	};
}

/**
 * 차수별 게임 카드를 다시 받는다. 벌칙 상태는 정산 응답의 penalties에서 찾는다.
 * @param {HTMLElement} box 게임 목록 영역
 * @param {{ctx: object, directory: Map}} state 탭 상태
 * @param {() => Promise<void>} reload 다시 그리기
 * @returns {Promise<void>}
 */
async function renderGameList(box, state, reload) {
	const {ctx} = state;
	const [settlement, ...perRound] = await Promise.all([api.get(`/api/meetings/${ctx.meetingId}/settlement`),
		...ctx.detail.rounds.map((round) => api.get(`/api/rounds/${round.id}/games`))]);
	const sections = ctx.detail.rounds.map((round, i) => html`<h3 class="${i ? 'band' : ''}">${round.name} 게임</h3>
		${raw(perRound[i].length ? perRound[i].map((game) => gameCard(game, settlement.penalties, state)).join('')
		: html`<p class="empty">아직 게임이 없어요</p>`)}`);
	box.innerHTML = html`<div class="game-list-area">${raw(sections.join(''))}</div>`;
	box.querySelector('.game-list-area').addEventListener('click', (event) => {
		const button = event.target.closest('button[data-game-action]');
		if (button) {
			runAction(event, async () => {
				await runGameAction(button.dataset.game, button.dataset.gameAction);
				await reload();
			});
		}
	});
}

/**
 * 게임 카드 하나: 종류 아이콘·상태 칩, 벌칙, 참가자 칩, 결과, 실행 화면·운영 버튼.
 * @param {{gameId: number, type: string, status: string, penaltyKind: string, penaltyNote: string|null,
 *   rerunOfId: number|null, participants: Array, result: object|null, createdBy: number}} game 게임 상태
 * @param {Array<{memberId: number, status: string, game: {gameId: number}}>} penalties 정산 벌칙 목록
 * @param {{ctx: object, directory: Map}} state 탭 상태
 * @returns {string} HTML
 */
function gameCard(game, penalties, state) {
	game.participants.forEach((p) => state.directory.set(p.memberId, state.directory.get(p.memberId) ?? p));
	const buttons = canOperate(game, state.ctx) ? availableActions(game).map((action) => html`
		<button type="button" class="secondary" data-game="${game.gameId}" data-game-action="${action}">${ACTION_LABELS[action]}</button>`) : [];
	const names = game.participants.map((p) => html`<span class="chip">${raw(nameOf(state.directory, p.memberId))}</span>`);
	return html`<article>
		<div class="list-row">
			<span class="icon-tile">${GAME_ICONS[game.type] ?? '🎲'}</span>
			<span class="row-body"><span class="row-title">${GAME_TYPES[game.type]}${game.rerunOfId ? ' (다시 돌린 회차)' : ''}</span>
				<span class="row-sub">${PENALTY_KINDS[game.penaltyKind]}${game.penaltyNote ? ` · ${game.penaltyNote}` : ''}</span></span>
			<span class="chip ${STATUS_TONES[game.status] ?? ''}">${GAME_STATUS[game.status] ?? game.status}</span>
		</div>
		<div class="people-line"><small>참가</small><div>${raw(names.join(' '))}</div></div>
		${raw(resultLine(game, penalties, state.directory))}
		<div class="button-row">
			<a href="/meetings/${state.ctx.meetingId}/games/${game.gameId}" data-link role="button">▶ 실행 화면</a>
			${raw(buttons.join(''))}
		</div>
	</article>`;
}

/**
 * 결과 줄: 벌칙자(+ 벌칙 상태) 또는 순서.
 * @param {{gameId: number, result: {penaltyMemberIds: Array<number>, orderedMemberIds: Array<number>}|null}} game 게임
 * @param {Array<{memberId: number, status: string, game: {gameId: number}}>} penalties 정산 벌칙 목록
 * @param {Map<number, object>} directory 이름표
 * @returns {string} HTML
 */
function resultLine(game, penalties, directory) {
	if (!game.result) {
		return '';
	}
	if (game.result.orderedMemberIds?.length) {
		const order = game.result.orderedMemberIds.map((id, i) => html`<span class="chip primary">${i + 1}. ${raw(nameOf(directory, id))}</span>`);
		return html`<div class="people-line"><small>순서</small><div>${raw(order.join(' '))}</div></div>`;
	}
	const statusOf = (id) => penalties.find((p) => p.game?.gameId === game.gameId && p.memberId === id)?.status;
	const names = game.result.penaltyMemberIds.map((id) => {
		const status = statusOf(id);
		return html`<span class="chip danger">${raw(nameOf(directory, id))}${status ? ` · ${PENALTY_STATUS[status] ?? status}` : ''}</span>`;
	});
	return html`<div class="people-line"><small>🎯 벌칙</small><div>${raw(names.join(' '))}</div></div>`;
}

/**
 * 게임 만들기 폼. 차수를 고르면 그 차수 참석자와 모임 게스트를 참가자 후보로 보여 준다.
 * @param {HTMLElement} box 폼 영역
 * @param {{rounds: Array<{roundId: number, name: string, attend: Array}>}} board 참석 현황판
 * @param {{ctx: object}} state 탭 상태
 * @param {() => Promise<void>} reload 게임 목록 다시 그리기
 */
function renderCreateForm(box, board, state, reload) {
	const types = Object.entries(GAME_TYPES).map(([value, label]) => html`<option value="${value}">${label}</option>`);
	const kinds = ['MONEY', 'ACTION'].map((value) => html`<option value="${value}">${PENALTY_KINDS[value]}</option>`);
	box.innerHTML = html`<h3 class="band">새 게임</h3><details><summary>🎲 게임 만들기</summary><form class="game-form">
		<label>차수 <select name="roundId">${raw(board.rounds.map((r) => html`<option value="${r.roundId}">${r.name}</option>`).join(''))}</select></label>
		<label>종류 <select name="type">${raw(types.join(''))}</select></label>
		<label data-penalty>벌칙 종류 <select name="penaltyKind">${raw(kinds.join(''))}</select></label>
		<label data-penalty>벌칙 안내(선택) <input name="penaltyNote" maxlength="100" placeholder="예: 2차 계산"></label>
		<label data-penalty>벌칙자 수 <input name="penaltyCount" type="number" min="1" value="1"></label>
		<fieldset class="participants"></fieldset>
		<button type="submit" class="block-button">만들기</button>
	</form></details>`;
	const form = box.querySelector('.game-form');
	const sync = () => syncCreateForm(form, board, state.ctx.detail.guests);
	form.addEventListener('change', (event) => {
		if (['roundId', 'type'].includes(event.target.name)) {
			sync();
		}
	});
	form.addEventListener('submit', (event) => runAction(event, async () => {
		await api.post(`/api/rounds/${form.elements.roundId.value}/games`, gameRequest(form));
		toast('게임을 만들었어요');
		await reload();
	}));
	sync();
}

/**
 * 고른 차수의 참가자 후보를 그리고, 순서 정하기면 벌칙 입력을 숨긴다.
 * @param {HTMLFormElement} form 게임 폼
 * @param {{rounds: Array<{roundId: number, attend: Array}>}} board 참석 현황판
 * @param {Array<{guestId: number, name: string}>} guests 모임 게스트
 */
function syncCreateForm(form, board, guests) {
	const round = board.rounds.find((r) => String(r.roundId) === form.elements.roundId.value);
	const people = [...(round?.attend ?? []).map((m) => ({id: m.memberId, label: `${m.emoji ? `${m.emoji} ` : ''}${m.nickname}`})),
		...guests.map((g) => ({id: g.guestId, label: `${g.name} (게스트)`}))];
	form.querySelector('.participants').innerHTML = html`<legend>참가자(2명 이상)</legend>
		${raw(people.map((p) => html`<label class="check-row"><input type="checkbox" name="participant" value="${p.id}" checked> ${p.label}</label>`).join('')
		|| html`<p class="muted">이 차수 참석자가 없어요</p>`)}`;
	const order = form.elements.type.value === 'ORDER';
	form.querySelectorAll('[data-penalty]').forEach((label) => {
		label.hidden = order;
	});
}

/**
 * 게임 폼을 GameCreateRequest로 바꾼다. 순서 정하기는 벌칙 없음·벌칙자 수 없음.
 * @param {HTMLFormElement} form 게임 폼
 * @returns {object} 요청 본문
 */
function gameRequest(form) {
	const order = form.elements.type.value === 'ORDER';
	return {
		type: form.elements.type.value,
		penaltyKind: order ? 'NONE' : form.elements.penaltyKind.value,
		penaltyNote: order ? null : form.elements.penaltyNote.value || null,
		penaltyCount: order ? null : Number(form.elements.penaltyCount.value),
		participantMemberIds: [...form.querySelectorAll('input[name="participant"]:checked')].map((i) => Number(i.value)),
	};
}
