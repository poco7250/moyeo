/*
 * 정산 탭(S-1~S-5, S-7, S-8, S-10, S-11, R-9, H-3). 지출 변경은 실시간 이벤트가 없어(2026-11-23 결정)
 * "새로고침" 버튼과 기존 이벤트(재확정 필요·재확정·영수증·벌칙 조건 초기화·게임 상태)로 다시 받는다.
 */

import {ApiError, api} from '../../api.js';
import {memberDirectory, runAction, formatWon} from '../../common.js';
import {html, raw, toast} from '../../ui.js';
import {mountExpenses} from './settlementExpenses.js';
import {mountHistory, mountMeetingCard} from './settlementHistory.js';
import {renderPenalties} from './settlementPenalties.js';
import {renderSummary, warningText} from './settlementSummary.js';

const EVENTS = new Set(['SETTLEMENT_REOPENED', 'SETTLEMENT_RECONFIRMED', 'EXPENSE_RECEIPT_CHANGED',
	'PENALTY_CONDITION_RESET', 'GAME_STATE_CHANGED']);
const STATUS_LABELS = {DRAFT: '정산 전', CONFIRMED: '정산 확정', REOPENED: '재확정 필요'};

/**
 * 정산 탭을 그린다.
 * @param {HTMLElement} container 탭 본문
 * @param {{meetingId: string, me: {id: number}, detail: object}} ctx 모임 화면 상태
 * @returns {Promise<{events: Set<string>, refresh: Function}>} 실시간 갱신 핸들
 */
export async function mountSettlementTab(container, ctx) {
	container.innerHTML = html`<div class="settlement-tab">
		<section class="settle-head"></section>
		<section class="settle-summary"></section>
		<section class="settle-penalties"></section>
		<section class="settle-expenses"></section>
		<section class="settle-history"></section>
		<section class="settle-card"></section>
	</div>`;
	const tab = container.querySelector('.settlement-tab');
	const state = {ctx, directory: await memberDirectory(ctx.detail)};
	const refresh = () => refreshAll(tab, state, expenses);
	state.reload = refresh;
	const expenses = mountExpenses(tab.querySelector('.settle-expenses'), state);
	mountHistory(tab.querySelector('.settle-history'), state);
	await refresh();
	await mountMeetingCard(tab.querySelector('.settle-card'), ctx);
	return {events: EVENTS, refresh};
}

/**
 * 정산·지출을 다시 받아 데이터 영역을 그린다.
 * @param {HTMLElement} tab 정산 탭
 * @param {{ctx: object, directory: Map}} state 탭 상태
 * @param {{render: (expenses: Array) => void}} expenses 지출 영역 핸들
 * @returns {Promise<void>}
 */
async function refreshAll(tab, state, expenses) {
	const base = `/api/meetings/${state.ctx.meetingId}`;
	const [settlement, expenseList] = await Promise.all([api.get(`${base}/settlement`), api.get(`${base}/expenses`)]);
	Object.assign(state, {settlement, expenses: expenseList});
	renderHead(tab.querySelector('.settle-head'), state);
	renderSummary(tab.querySelector('.settle-summary'), state);
	renderPenalties(tab.querySelector('.settle-penalties'), state);
	expenses.render(expenseList);
}

/**
 * 상단: 상태·리비전 라벨·새로고침, 재확정 필요 배너, 확정·재확정 버튼(정산 권한).
 * @param {HTMLElement} box 상단 영역
 * @param {{ctx: object, settlement: object, expenses: Array}} state 탭 상태
 */
function renderHead(box, state) {
	const {settlement, ctx} = state;
	const label = settlement.preview ? '미리보기' : `리비전 ${settlement.basisRevisionNo} 기준`;
	const total = state.expenses.reduce((sum, expense) => sum + expense.amount, 0);
	box.innerHTML = html`<div class="settle-head-area">
		<h3>${STATUS_LABELS[settlement.status] ?? settlement.status} <small class="muted">${label}</small></h3>
		<p>총 지출 ${formatWon(total)}</p>
		${raw(settlement.status === 'REOPENED' ? html`<p><mark>정산이 바뀌었어요. 재확정이 필요해요. (아래 "정산 이력"에서 변경 내역을 볼 수 있어요)</mark></p>` : '')}
		<button type="button" class="secondary outline" data-act="refresh">새로고침</button>
		${raw(ctx.detail.settlementAuthority && settlement.status !== 'CONFIRMED'
		? html`<button type="button" data-act="confirm">${settlement.status === 'REOPENED' ? '재확정' : '정산 확정'}</button>` : '')}
	</div>`;
	box.querySelector('.settle-head-area').addEventListener('click', (event) => {
		const act = event.target.closest('[data-act]')?.dataset.act;
		if (act) {
			runAction(event, async () => {
				if (act === 'confirm') {
					await confirmSettlement(ctx.meetingId, false);
				}
				await state.reload();
			});
		}
	});
}

/**
 * 정산을 확정·재확정한다. 경고가 있으면 확인받고 다시 보내고, 막힌 사유는 상세 목록과 함께 알려 준다.
 * @param {string} meetingId 모임 id
 * @param {boolean} acknowledgeWarnings 경고 확인 여부
 * @returns {Promise<void>}
 */
async function confirmSettlement(meetingId, acknowledgeWarnings) {
	try {
		const result = await api.post(`/api/meetings/${meetingId}/settlement/confirm`, {acknowledgeWarnings});
		toast(`확정했어요 (리비전 ${result.basisRevisionNo})`);
	} catch (error) {
		if (!(error instanceof ApiError)) {
			throw error;
		}
		if (error.code === 'WARNING_UNCONFIRMED' && window.confirm(`${error.message}\n${warningText(error.details)}\n그래도 확정할까요?`)) {
			await confirmSettlement(meetingId, true);
			return;
		}
		if (['PENALTY_CONDITION_UNSET', 'MONEY_GAME_IN_PROGRESS'].includes(error.code)) {
			throw new Error(`${error.message} (${(error.details ?? []).map((g) => g.roundName).join(', ')} 게임)`);
		}
		if (error.code !== 'WARNING_UNCONFIRMED') {
			throw error;
		}
	}
}
