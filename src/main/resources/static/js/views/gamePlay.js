/*
 * 게임 실행 화면(PRD 5장 3번, R-0~R-3, R-8). 큰 글씨로 한 폰을 돌려 써도 되게 한다.
 * 결과는 서버 값 그대로 보여 주고 연출만 종류별로 다르다. 게임 토픽을 구독해 상태가 바뀌면 다시 받는다.
 */

import {api} from '../api.js';
import {memberName, runAction} from '../common.js';
import {subscribe} from '../realtime.js';
import {navigate} from '../router.js';
import {getMe} from '../session.js';
import {html, raw, render, showError} from '../ui.js';
import {ACTION_LABELS, GAME_STATUS, GAME_TYPES, PENALTY_KINDS, availableActions, canOperate, runGameAction}
	from './meeting/gameCommon.js';

const STEP_MILLIS = {LADDER: 700, DRAW: 500, ORDER: 400};
const ROULETTE_SPIN_MILLIS = 2500;
const ROULETTE_TICK_MILLIS = 90;

/**
 * 게임 실행 화면을 그리고 게임 토픽을 구독한다. 돌려준 함수가 구독과 연출 타이머를 정리한다.
 * @param {{params: {meetingId: string, gameId: string}}} ctx 라우터 컨텍스트
 * @returns {Promise<() => void>} 정리 함수
 */
export async function gamePlayView({params}) {
	const [me, detail] = await Promise.all([getMe(), api.get(`/api/meetings/${encodeURIComponent(params.meetingId)}`)]);
	const view = {ctx: {me, detail, meetingId: params.meetingId}, gameId: params.gameId, timers: new Set(), lastStatus: null};
	const root = render(html`<div class="play">
		<p><a href="/meetings/${params.meetingId}?tab=game" data-link>← 게임 목록</a></p>
		<div class="play-body"></div>
	</div>`).querySelector('.play');
	view.body = root.querySelector('.play-body');
	const reload = () => loadAndRender(view).catch(showError);
	await loadAndRender(view);
	const unsubscribe = subscribe(`/topic/games/${view.gameId}`, reload, {onResync: reload});
	return () => {
		unsubscribe();
		clearTimers(view);
	};
}

/**
 * 게임 상태를 받아 그린다. 이번 화면에서 막 공개됐을 때만 연출을 돌린다.
 * @param {{gameId: string, body: HTMLElement, ctx: object, timers: Set, lastStatus: string|null}} view 화면 상태
 * @returns {Promise<void>}
 */
async function loadAndRender(view) {
	const game = await api.get(`/api/games/${encodeURIComponent(view.gameId)}/state`);
	const justRevealed = view.lastStatus !== null && view.lastStatus !== 'REVEALED' && game.status === 'REVEALED';
	view.lastStatus = game.status;
	clearTimers(view);
	view.body.innerHTML = html`<div class="play-area">
		<h1>${GAME_TYPES[game.type]}</h1>
		<p class="play-status">${GAME_STATUS[game.status] ?? game.status} · ${PENALTY_KINDS[game.penaltyKind]}
			${game.penaltyNote ? ` · ${game.penaltyNote}` : ''}</p>
		<div class="play-stage"></div>
		<div class="play-actions">${raw(actionButtons(game, view.ctx))}</div>
	</div>`;
	bindActions(view.body.querySelector('.play-area'), game, () => loadAndRender(view));
	const stage = view.body.querySelector('.play-stage');
	if (game.result) {
		(justRevealed ? animate : showFinal)(stage, game, view);
	} else {
		stage.innerHTML = html`<ul class="play-names">${raw(game.participants.map((p) => html`<li>${raw(memberName(p))}</li>`).join(''))}</ul>`;
	}
}

/**
 * 운영 버튼(진행자·호스트만).
 * @param {{status: string, createdBy: number}} game 게임 상태
 * @param {object} ctx 모임 화면 상태
 * @returns {string} HTML
 */
function actionButtons(game, ctx) {
	if (!canOperate(game, ctx)) {
		return '';
	}
	return availableActions(game).map((action) => html`<button type="button" data-action="${action}"
		class="${action === 'start' ? '' : 'secondary'}">${ACTION_LABELS[action]}</button>`).join('');
}

/**
 * 운영 버튼을 처리한다. 다시 돌리기면 새 회차 화면으로 간다.
 * @param {HTMLElement} area 화면 안쪽 요소
 * @param {{gameId: number}} game 게임 상태
 * @param {() => Promise<void>} reload 다시 그리기
 */
function bindActions(area, game, reload) {
	area.querySelector('.play-actions').addEventListener('click', (event) => {
		const action = event.target.closest('[data-action]')?.dataset.action;
		if (action) {
			runAction(event, async () => {
				const next = await runGameAction(game.gameId, action);
				if (next && action === 'rerun') {
					navigate(window.location.pathname.replace(/\/games\/\d+$/, `/games/${next.gameId}`), {replace: true});
					return;
				}
				await reload();
			});
		}
	});
}

/**
 * 결과를 연출 없이 바로 보여 준다.
 * @param {HTMLElement} stage 무대
 * @param {object} game 게임 상태(result 포함)
 */
function showFinal(stage, game) {
	stage.innerHTML = resultHtml(game, game.participants.length);
}

/**
 * 종류별 연출 후 결과를 보여 준다. 사다리·꽝뽑기·순서는 한 명씩, 룰렛은 이름을 돌리다 멈춘다.
 * @param {HTMLElement} stage 무대
 * @param {object} game 게임 상태(result 포함)
 * @param {{timers: Set}} view 화면 상태(타이머 정리용)
 */
function animate(stage, game, view) {
	if (game.type === 'ROULETTE') {
		spinRoulette(stage, game, view);
		return;
	}
	const total = game.type === 'ORDER' ? game.result.orderedMemberIds.length : game.participants.length;
	for (let shown = 0; shown <= total; shown += 1) {
		later(view, () => {
			stage.innerHTML = resultHtml(game, shown);
		}, shown * STEP_MILLIS[game.type]);
	}
}

/**
 * 룰렛: 참가자 이름을 빠르게 돌리다가 서버 결과를 보여 준다.
 * @param {HTMLElement} stage 무대
 * @param {object} game 게임 상태
 * @param {{timers: Set}} view 화면 상태
 */
function spinRoulette(stage, game, view) {
	const ticks = Math.floor(ROULETTE_SPIN_MILLIS / ROULETTE_TICK_MILLIS);
	for (let i = 0; i < ticks; i += 1) {
		const member = game.participants[i % game.participants.length];
		later(view, () => {
			stage.innerHTML = html`<p class="play-spin">${raw(memberName(member))}</p>`;
		}, i * ROULETTE_TICK_MILLIS);
	}
	later(view, () => showFinal(stage, game), ROULETTE_SPIN_MILLIS);
}

/**
 * 결과 HTML. shown명까지만 공개한다(나머지는 ?).
 * @param {{type: string, participants: Array, result: {penaltyMemberIds: Array<number>, orderedMemberIds: Array<number>}}} game 게임
 * @param {number} shown 공개할 인원
 * @returns {string} HTML
 */
function resultHtml(game, shown) {
	const byId = new Map(game.participants.map((p) => [p.memberId, p]));
	if (game.type === 'ORDER') {
		const items = game.result.orderedMemberIds.slice(0, shown).map((id) => html`<li>${raw(memberName(byId.get(id)))}</li>`);
		return html`<ol class="play-names">${raw(items.join(''))}</ol>`;
	}
	const losers = new Set(game.result.penaltyMemberIds);
	const cells = game.participants.map((p, i) => {
		const open = i < shown;
		const lose = losers.has(p.memberId);
		return html`<li class="${open && lose ? 'play-lose' : ''}">${raw(memberName(p))} — ${open ? (lose ? (game.type === 'DRAW' ? '💣 꽝!' : '🎯 벌칙') : '통과') : '?'}</li>`;
	});
	return html`<ul class="play-names">${raw(cells.join(''))}</ul>`;
}

/**
 * 연출 타이머를 건다(화면을 떠나면 clearTimers로 취소).
 * @param {{timers: Set}} view 화면 상태
 * @param {() => void} task 할 일
 * @param {number} delay 지연(ms)
 */
function later(view, task, delay) {
	const id = setTimeout(() => {
		view.timers.delete(id);
		task();
	}, delay);
	view.timers.add(id);
}

/**
 * 걸어 둔 연출 타이머를 모두 취소한다.
 * @param {{timers: Set}} view 화면 상태
 */
function clearTimers(view) {
	view.timers.forEach((id) => clearTimeout(id));
	view.timers.clear();
}
