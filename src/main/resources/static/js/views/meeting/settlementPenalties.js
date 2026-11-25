/*
 * 금액 벌칙 반영 내역(S-4, 2026-11-24 개정). 금액 벌칙 게임마다 금액·벌칙자·나눠 받는 사람을 보여 주고,
 * 정산 권한자에게 면제·면제 취소를 준다. 금액은 게임을 만들 때 정해지므로 여기서 입력하지 않는다.
 */

import {api} from '../../api.js';
import {formatWon, nameOf, runAction} from '../../common.js';
import {html, raw, toast} from '../../ui.js';
import {GAME_TYPES, PENALTY_STATUS} from './gameCommon.js';

/**
 * 벌칙 영역을 그린다. 금액 벌칙이 없으면 비운다.
 * @param {HTMLElement} box 벌칙 영역
 * @param {{ctx: object, directory: Map, settlement: {penalties: Array}, reload: Function}} state 정산 탭 상태
 */
export function renderPenalties(box, state) {
	const games = groupByGame(state.settlement.penalties.filter((p) => p.status !== 'VOIDED'));
	if (games.length === 0) {
		box.innerHTML = '';
		return;
	}
	box.innerHTML = html`<div class="penalty-area"><h3 class="band">벌칙 금액</h3>
		${raw(games.map((penalties) => penaltyCard(penalties, state)).join(''))}</div>`;
	box.querySelector('.penalty-area').addEventListener('click', (event) => onPenaltyClick(event, state));
}

/**
 * 벌칙을 게임별로 묶는다.
 * @param {Array<{game: {gameId: number}}>} penalties 벌칙
 * @returns {Array<Array<object>>} 게임별 벌칙 목록
 */
function groupByGame(penalties) {
	const byGame = new Map();
	penalties.forEach((p) => byGame.set(p.game.gameId, [...(byGame.get(p.game.gameId) ?? []), p]));
	return [...byGame.values()];
}

/**
 * 게임 하나의 벌칙 카드: 금액·상태, 벌칙자(게스트는 "앱 밖에서"), 나눠 받는 사람, 면제 버튼.
 * @param {Array<{memberId: number, status: string, guest: boolean, game: {gameId: number, type: string, penaltyAmount: number|null},
 *   chargeAmount: number|null, creditedMemberIds: Array<number>}>} penalties 이 게임 벌칙
 * @param {{ctx: object, directory: Map}} state 정산 탭 상태
 * @returns {string} HTML
 */
function penaltyCard(penalties, state) {
	const [first] = penalties;
	const amount = first.game.penaltyAmount;
	const tone = {APPLIED: 'success', WAIVED: '', ASSIGNED: 'warn'}[first.status] ?? '';
	return html`<article>
		<div class="card-head"><h4>🎯 ${GAME_TYPES[first.game.type]}${amount != null ? ` · 당첨자 ${formatWon(amount)}` : ''}</h4>
			<span class="chip ${tone}">${PENALTY_STATUS[first.status] ?? first.status}</span></div>
		<div class="people-line"><small>벌칙자</small><div>${raw(penalties.map((p) => penalizedChip(p, state.directory)).join(' '))}</div></div>
		${raw(creditedLine(first, state.directory))}
		${raw(state.ctx.detail.settlementAuthority ? waiverButton(first) : '')}
	</article>`;
}

/**
 * 벌칙자 칩. 게스트는 정산에 안 들어가 "앱 밖에서"로, 정산 반영분은 부담액을 붙인다.
 * @param {{memberId: number, guest: boolean, chargeAmount: number|null}} penalty 벌칙
 * @param {Map<number, object>} directory 이름표
 * @returns {string} HTML
 */
function penalizedChip(penalty, directory) {
	const suffix = penalty.guest ? ' · 앱 밖에서' : (penalty.chargeAmount != null ? ` +${formatWon(penalty.chargeAmount)}` : '');
	return html`<span class="chip danger">${raw(nameOf(directory, penalty.memberId))}${suffix}</span>`;
}

/**
 * 나눠 받는 사람 줄. 정산에 반영 중일 때만 보인다.
 * @param {{status: string, creditedMemberIds: Array<number>}} penalty 대표 벌칙
 * @param {Map<number, object>} directory 이름표
 * @returns {string} HTML
 */
function creditedLine(penalty, directory) {
	if (penalty.status !== 'APPLIED') {
		return '';
	}
	if (!penalty.creditedMemberIds.length) {
		return html`<p class="muted"><small>나눠 받을 참가자가 없어 정산에는 반영되지 않아요.</small></p>`;
	}
	const names = penalty.creditedMemberIds.map((id) => html`<span class="chip">${raw(nameOf(directory, id))}</span>`);
	return html`<div class="people-line"><small>덜 내는 사람</small><div>${raw(names.join(' '))}</div></div>`;
}

/**
 * 면제(정산 반영 중) 또는 면제 취소(면제됨) 버튼. 금액 없는 옛 게임은 버튼이 없다.
 * @param {{status: string, game: {gameId: number}}} penalty 대표 벌칙
 * @returns {string} HTML
 */
function waiverButton(penalty) {
	const gameId = penalty.game.gameId;
	if (penalty.status === 'APPLIED') {
		return html`<button type="button" class="secondary block-button" data-act="waive" data-game="${gameId}">이번엔 면제</button>`;
	}
	if (penalty.status === 'WAIVED') {
		return html`<button type="button" class="secondary block-button" data-act="unwaive" data-game="${gameId}">면제 취소</button>`;
	}
	return '';
}

/**
 * 면제·면제 취소 버튼을 처리한다.
 * @param {MouseEvent} event 클릭 이벤트
 * @param {{reload: () => Promise<void>}} state 정산 탭 상태
 */
function onPenaltyClick(event, state) {
	const button = event.target.closest('button[data-act]');
	if (!button) {
		return;
	}
	const url = `/api/games/${button.dataset.game}/penalty-waiver`;
	const waive = button.dataset.act === 'waive';
	runAction(event, async () => {
		await (waive ? api.post(url) : api.delete(url));
		toast(waive ? '벌칙 금액을 면제했어요' : '면제를 취소했어요');
		await state.reload();
	});
}
