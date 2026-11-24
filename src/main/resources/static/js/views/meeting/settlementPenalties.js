/*
 * 벌칙 조건(R-9, E2). 금액 벌칙 게임마다 벌칙자·상태·조건을 보여 주고, 정산 권한자에게 조건 입력
 * (고정 금액·비율·전액, 대상 지출 1건), 조건 해제, "금액 벌칙 없음"·면제 취소를 준다.
 */

import {api} from '../../api.js';
import {formatWon, nameOf, runAction} from '../../common.js';
import {html, raw, toast} from '../../ui.js';
import {GAME_TYPES, PENALTY_STATUS} from './gameCommon.js';

/**
 * 벌칙 조건 영역을 그린다. 금액 벌칙이 없으면 비운다.
 * @param {HTMLElement} box 벌칙 영역
 * @param {{ctx: object, directory: Map, settlement: {penalties: Array}, expenses: Array, reload: Function}} state 정산 탭 상태
 */
export function renderPenalties(box, state) {
	const games = groupByGame(state.settlement.penalties.filter((p) => p.status !== 'VOIDED'));
	if (games.length === 0) {
		box.innerHTML = '';
		return;
	}
	box.innerHTML = html`<div class="penalty-area"><h3 class="band">벌칙 금액</h3>
		${raw(games.map((penalties) => penaltyCard(penalties, state)).join(''))}</div>`;
	const area = box.querySelector('.penalty-area');
	area.addEventListener('submit', (event) => runAction(event, async () => {
		await api.put(`/api/games/${event.target.dataset.game}/penalty-rule`, ruleBody(event.target));
		toast('벌칙 조건을 적용했어요');
		await state.reload();
	}));
	area.addEventListener('click', (event) => onPenaltyClick(event, state));
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
 * 게임 하나의 벌칙 카드.
 * @param {Array<{memberId: number, status: string, game: object, rule: object|null, chargeAmount: number|null}>} penalties 이 게임 벌칙
 * @param {{ctx: object, directory: Map, expenses: Array}} state 정산 탭 상태
 * @returns {string} HTML
 */
function penaltyCard(penalties, state) {
	const [first] = penalties;
	const people = penalties.map((p) => html`<span class="chip danger">${raw(nameOf(state.directory, p.memberId))}${p.chargeAmount != null ? ` ${formatWon(p.chargeAmount)}` : ''}</span>`);
	const editable = state.ctx.detail.settlementAuthority;
	const tone = {APPLIED: 'success', ASSIGNED: 'warn'}[first.status] ?? '';
	return html`<article>
		<div class="card-head"><h4>🎯 ${first.game.roundName} · ${GAME_TYPES[first.game.type]}</h4>
			<span class="chip ${tone}">${PENALTY_STATUS[first.status] ?? first.status}</span></div>
		<div class="people-line"><small>벌칙자</small><div>${raw(people.join(' '))}</div></div>
		${raw(first.rule ? html`<p class="muted"><small>${ruleText(first.rule)}</small></p>` : '')}
		${raw(editable ? ruleForm(first, state.expenses, state.ctx.detail.rounds) : '')}
	</article>`;
}

/**
 * 지금 조건을 글로 바꾼다.
 * @param {{moneyType: string, amount: number|null, ratioPct: number|null, targetExpense: {roundName: string, memo: string|null, amount: number}}} rule 조건
 * @returns {string} 안내 글
 */
function ruleText(rule) {
	const how = rule.moneyType === 'FIXED_AMOUNT' ? `고정 ${formatWon(rule.amount)}`
		: (rule.ratioPct === 100 ? '전액' : `${rule.ratioPct}%`);
	const target = rule.targetExpense;
	return `대상: ${target.roundName} ${target.memo ?? '지출'}(${formatWon(target.amount)}) · ${how}`;
}

/**
 * 조건 입력 폼과 상태별 버튼(해제·금액 벌칙 없음·면제 취소).
 * @param {{status: string, game: {gameId: number}, rule: object|null}} penalty 대표 벌칙
 * @param {Array<{id: number, roundId: number, memo: string|null, amount: number}>} expenses 지출
 * @param {Array<{id: number, name: string}>} rounds 차수
 * @returns {string} HTML
 */
function ruleForm(penalty, expenses, rounds) {
	const roundName = (id) => rounds.find((r) => r.id === id)?.name ?? '';
	const targets = expenses.map((e) => html`<option value="${e.id}" ${raw(penalty.rule?.targetExpense?.expenseId === e.id ? 'selected' : '')}>
		${roundName(e.roundId)} ${e.memo ?? '지출'} ${formatWon(e.amount)}</option>`);
	const gameId = penalty.game.gameId;
	if (penalty.status === 'WAIVED') {
		return html`<button type="button" class="secondary block-button" data-act="unwaive" data-game="${gameId}">면제 취소</button>`;
	}
	return html`<form data-game="${gameId}" class="rule-form">
		<select name="moneyType"><option value="RATIO">비율</option><option value="FULL">전액</option><option value="FIXED_AMOUNT">고정 금액</option></select>
		<input name="value" type="number" min="1" placeholder="비율(%) 또는 금액(원)">
		<select name="targetExpenseId" required>${raw(targets.join('') || html`<option value="">지출을 먼저 넣어 주세요</option>`)}</select>
		<button type="submit" class="block-button">조건 적용</button>
		<div class="button-row">
			<button type="button" class="secondary" data-act="waive" data-game="${gameId}">금액 벌칙 없음</button>
			${raw(penalty.status === 'APPLIED' ? html`<button type="button" class="secondary" data-act="clear" data-game="${gameId}">조건 해제</button>` : '')}
		</div>
	</form>`;
}

/**
 * 조건 폼을 PenaltyRuleRequest로 바꾼다. 전액은 비율 100%다.
 * @param {HTMLFormElement} form 조건 폼
 * @returns {{moneyType: string, targetExpenseId: number, amount: number|null, ratioPct: number|null}} 요청 본문
 */
function ruleBody(form) {
	const type = form.elements.moneyType.value;
	const value = Number(form.elements.value.value) || null;
	return {
		moneyType: type === 'FULL' ? 'RATIO' : type,
		targetExpenseId: Number(form.elements.targetExpenseId.value),
		amount: type === 'FIXED_AMOUNT' ? value : null,
		ratioPct: type === 'FULL' ? 100 : (type === 'RATIO' ? value : null),
	};
}

/**
 * 해제·금액 벌칙 없음·면제 취소 버튼을 처리한다.
 * @param {MouseEvent} event 클릭 이벤트
 * @param {{reload: () => Promise<void>}} state 정산 탭 상태
 */
function onPenaltyClick(event, state) {
	const button = event.target.closest('button[data-act]');
	if (!button) {
		return;
	}
	const base = `/api/games/${button.dataset.game}`;
	const requests = {
		clear: () => api.delete(`${base}/penalty-rule`),
		waive: () => api.post(`${base}/penalty-waiver`),
		unwaive: () => api.delete(`${base}/penalty-waiver`),
	};
	runAction(event, async () => {
		await requests[button.dataset.act]();
		await state.reload();
	});
}
