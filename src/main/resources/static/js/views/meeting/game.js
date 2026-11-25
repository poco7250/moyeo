/*
 * 게임 탭(R-0~R-5, R-8, R-10). 모임 게임 카드(데이터, 실시간으로 다시 그림)와 게임 만들기 폼(한 번만 그림),
 * 보드게임 기록(R-7)을 둔다. 결과 연출은 게임 실행 화면(/meetings/{id}/games/{gameId})에서 본다.
 */

import {api} from '../../api.js';
import {formatWon, memberDirectory, nameOf, runAction} from '../../common.js';
import {html, raw, toast} from '../../ui.js';
import {mountBoardGames} from './boardGame.js';
import {ACTION_LABELS, GAME_STATUS, GAME_TYPES, PENALTY_KINDS, PENALTY_STATUS, availableActions, canOperate,
	runGameAction} from './gameCommon.js';

const GAME_ICONS = {LADDER: '🪜', DRAW: '🎟️', ROULETTE: '🎡', ORDER: '🔢'};
const STATUS_TONES = {WAITING: '', PLAYING: 'primary', REVEALED: 'warn', CLOSED: 'success', CANCELED: ''};
const EVENTS = new Set(['GAME_STATE_CHANGED', 'BOARD_GAME_RECORD_CHANGED', 'BOARD_GAME_RECORD_DELETED',
	'SETTLEMENT_REOPENED', 'SETTLEMENT_RECONFIRMED']);

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
 * 모임 게임 카드를 다시 받는다(최근에 만든 순). 벌칙 상태는 정산 응답의 penalties에서 찾는다.
 * @param {HTMLElement} box 게임 목록 영역
 * @param {{ctx: object, directory: Map}} state 탭 상태
 * @param {() => Promise<void>} reload 다시 그리기
 * @returns {Promise<void>}
 */
async function renderGameList(box, state, reload) {
	const {ctx} = state;
	const [settlement, games] = await Promise.all([api.get(`/api/meetings/${ctx.meetingId}/settlement`),
		api.get(`/api/meetings/${ctx.meetingId}/games`)]);
	const cards = games.length ? games.map((game) => gameCard(game, settlement.penalties, state)).join('')
		: html`<p class="empty">아직 게임이 없어요</p>`;
	box.innerHTML = html`<div class="game-list-area"><h3>게임</h3>${raw(cards)}</div>`;
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
 * @param {{gameId: number, type: string, status: string, penaltyKind: string, penaltyAmount: number|null, penaltyNote: string|null,
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
				<span class="row-sub">${penaltyLabel(game)}${game.penaltyNote ? ` · ${game.penaltyNote}` : ''}</span></span>
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
 * 벌칙 표시: 금액 벌칙이면 "당첨자 10,000원", 아니면 종류 이름.
 * @param {{penaltyKind: string, penaltyAmount: number|null}} game 게임
 * @returns {string} 표시 문구
 */
function penaltyLabel(game) {
	if (game.penaltyKind === 'MONEY' && game.penaltyAmount != null) {
		return `당첨자 ${formatWon(game.penaltyAmount)}`;
	}
	return PENALTY_KINDS[game.penaltyKind];
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
 * 게임 만들기 폼. 모임 아무 차수에든 참석한 사람과 모임 게스트를 참가자 후보로 보여 준다.
 * @param {HTMLElement} box 폼 영역
 * @param {{rounds: Array<{roundId: number, name: string, attend: Array}>}} board 참석 현황판
 * @param {{ctx: object}} state 탭 상태
 * @param {() => Promise<void>} reload 게임 목록 다시 그리기
 */
function renderCreateForm(box, board, state, reload) {
	const types = Object.entries(GAME_TYPES).map(([value, label]) => html`<option value="${value}">${label}</option>`);
	const kinds = ['MONEY', 'ACTION'].map((value) => html`<option value="${value}">${PENALTY_KINDS[value]}</option>`);
	box.innerHTML = html`<h3 class="band">새 게임</h3><details><summary>🎲 게임 만들기</summary><form class="game-form">
		<label>종류 <select name="type">${raw(types.join(''))}</select></label>
		<label data-penalty>벌칙 종류 <select name="penaltyKind">${raw(kinds.join(''))}</select></label>
		<label data-penalty data-money>당첨자가 낼 금액(원) <input name="penaltyAmount" type="number" min="1" max="100000000" step="1" required placeholder="예: 10000"></label>
		<label data-penalty>벌칙 안내(선택) <input name="penaltyNote" maxlength="100" placeholder="예: 커피값"></label>
		<label data-penalty>벌칙자 수 <input name="penaltyCount" type="number" min="1" value="1"></label>
		<fieldset class="participants"></fieldset>
		<button type="submit" class="block-button">만들기</button>
	</form></details>`;
	const form = box.querySelector('.game-form');
	fillParticipants(form, board, state.ctx.detail.guests);
	form.addEventListener('change', (event) => {
		if (['type', 'penaltyKind'].includes(event.target.name)) {
			syncPenaltyInputs(form);
		}
	});
	form.addEventListener('submit', (event) => runAction(event, async () => {
		await api.post(`/api/meetings/${state.ctx.meetingId}/games`, gameRequest(form));
		toast('게임을 만들었어요');
		await reload();
	}));
	syncPenaltyInputs(form);
}

/**
 * 참가자 후보: 모든 차수의 참석자(한 번씩, 차수 순)와 모임 게스트.
 * @param {HTMLFormElement} form 게임 폼
 * @param {{rounds: Array<{attend: Array}>}} board 참석 현황판
 * @param {Array<{guestId: number, name: string}>} guests 모임 게스트
 */
function fillParticipants(form, board, guests) {
	const attendees = new Map();
	board.rounds.flatMap((r) => r.attend ?? []).forEach((m) => attendees.set(m.memberId, m));
	const people = [...[...attendees.values()].map((m) => ({id: m.memberId, label: `${m.emoji ? `${m.emoji} ` : ''}${m.nickname}`})),
		...guests.map((g) => ({id: g.guestId, label: `${g.name} (게스트)`}))];
	form.querySelector('.participants').innerHTML = html`<legend>참가자(2명 이상)</legend>
		${raw(people.map((p) => html`<label class="check-row"><input type="checkbox" name="participant" value="${p.id}" checked> ${p.label}</label>`).join('')
		|| html`<p class="muted">아직 참석한다고 한 사람이 없어요</p>`)}`;
}

/**
 * 순서 정하기면 벌칙 입력을 숨기고, 금액 벌칙일 때만 금액 입력을 보인다(숨긴 칸은 필수 검사에서 뺀다).
 * @param {HTMLFormElement} form 게임 폼
 */
function syncPenaltyInputs(form) {
	const order = form.elements.type.value === 'ORDER';
	const money = !order && form.elements.penaltyKind.value === 'MONEY';
	form.querySelectorAll('[data-penalty]').forEach((label) => {
		label.hidden = order;
	});
	form.querySelector('[data-money]').hidden = !money;
	form.elements.penaltyAmount.disabled = !money;
}

/**
 * 게임 폼을 GameCreateRequest로 바꾼다. 순서 정하기는 벌칙 없음·벌칙자 수 없음, 금액은 금액 벌칙일 때만.
 * @param {HTMLFormElement} form 게임 폼
 * @returns {object} 요청 본문
 */
function gameRequest(form) {
	const order = form.elements.type.value === 'ORDER';
	const penaltyKind = order ? 'NONE' : form.elements.penaltyKind.value;
	return {
		type: form.elements.type.value,
		penaltyKind,
		penaltyAmount: penaltyKind === 'MONEY' ? Number(form.elements.penaltyAmount.value) : null,
		penaltyNote: order ? null : form.elements.penaltyNote.value || null,
		penaltyCount: order ? null : Number(form.elements.penaltyCount.value),
		participantMemberIds: [...form.querySelectorAll('input[name="participant"]:checked')].map((i) => Number(i.value)),
	};
}
