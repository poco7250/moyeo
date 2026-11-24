/* 정산 요약: 1인당 부담액·순잔액, 경고, 송금 안내(받는 사람 송금 정보·카톡 문구), 재확정 변동액 카드 */

import {copyText, formatWon, nameOf, safeLink, thumb} from '../../common.js';
import {html, raw} from '../../ui.js';

/**
 * 정산 요약을 그린다.
 * @param {HTMLElement} box 요약 영역
 * @param {{ctx: object, directory: Map, settlement: object}} state 탭 상태
 */
export function renderSummary(box, state) {
	const {settlement, directory} = state;
	const rows = settlement.members.map((m) => memberRow(m, directory));
	box.innerHTML = html`<div class="summary-area">
		${raw(changeCard(settlement.revisionChange, directory))}
		<h3 class="band">1인당 금액</h3>
		${raw(rows.length ? html`<ul class="list">${raw(rows.join(''))}</ul>` : html`<p class="empty">아직 지출이 없어요</p>`)}
		${raw(settlement.warnings.length ? html`<p><span class="chip warn">⚠️ ${warningText(settlement.warnings)}</span></p>` : '')}
		<h3 class="band">송금 안내</h3>
		<p class="muted"><small>송금은 토스·카카오페이 등 앱 밖에서 해요</small></p>
		${raw(transferList(settlement, directory))}
	</div>`;
	box.querySelector('.summary-area').addEventListener('click', (event) => {
		const button = event.target.closest('[data-copy]');
		if (button) {
			copyText(button.dataset.copy === 'transfers' ? transferText(settlement, directory) : button.dataset.copy);
		}
	});
}

/**
 * 멤버 한 줄: 썸네일·이름·부담액, 오른쪽에 받을 돈(초록)·낼 돈(빨강).
 * @param {{memberId: number, shareAmount: number, netBalance: number}} m 멤버 정산
 * @param {Map<number, object>} directory 이름표
 * @returns {string} HTML
 */
function memberRow(m, directory) {
	const member = directory.get(m.memberId);
	const receive = m.netBalance >= 0;
	return html`<li><div class="list-row">
		${raw(thumb(member?.nickname, m.memberId, {emoji: member?.emoji, small: true}))}
		<span class="row-body"><span class="row-title">${raw(nameOf(directory, m.memberId))}</span>
			<span class="row-sub">부담 ${formatWon(m.shareAmount)}</span></span>
		<span class="amount ${receive ? 'plus' : 'minus'}">${receive ? '+' : '-'}${formatWon(Math.abs(m.netBalance))}
			<small>${receive ? '받을 돈' : '낼 돈'}</small></span>
	</div></li>`;
}

/**
 * 송금 안내 목록과 받는 사람 송금 정보.
 * @param {{transfers: Array<{fromMemberId: number, toMemberId: number, amount: number}>, payees: Array}} settlement 정산
 * @param {Map<number, object>} directory 이름표
 * @returns {string} HTML
 */
function transferList(settlement, directory) {
	if (settlement.transfers.length === 0) {
		return html`<p class="muted">보낼 돈이 없어요</p>`;
	}
	const items = settlement.transfers.map((t) => {
		const payee = settlement.payees.find((p) => p.memberId === t.toMemberId);
		return html`<li><div class="list-row">
			<span class="row-body"><span class="row-title">${raw(nameOf(directory, t.fromMemberId))} → ${raw(nameOf(directory, t.toMemberId))}</span>
				${raw(payeeLinks(payee))}</span>
			<span class="amount">${formatWon(t.amount)}</span>
		</div></li>`;
	});
	return html`<ul class="list">${raw(items.join(''))}</ul>
		<button type="button" class="secondary block-button" data-copy="transfers">💬 송금 안내 카톡 문구 복사</button>`;
}

/**
 * 받는 사람 송금 링크(본인이 넣은 https 링크만)와 계좌 복사 버튼.
 * @param {{bankName: string|null, accountNumber: string|null, accountHolder: string|null, tossLink: string|null,
 *   kakaoPayLink: string|null}|undefined} payee 받는 사람 송금 정보
 * @returns {string} HTML
 */
function payeeLinks(payee) {
	if (!payee) {
		return '';
	}
	const account = payee.accountNumber ? `${payee.bankName ?? ''} ${payee.accountNumber} ${payee.accountHolder ?? ''}`.trim() : '';
	const chip = (link) => (link ? html`<span class="chip primary">${raw(link)}</span>` : '');
	return html`<span class="row-actions">
		${raw(chip(safeLink(payee.tossLink, '토스')))}
		${raw(chip(safeLink(payee.kakaoPayLink, '카카오페이')))}
		${raw(account ? html`<button type="button" class="secondary" data-copy="${account}">계좌 복사</button>` : '')}</span>`;
}

/**
 * 카톡에 붙여 넣을 송금 안내 문구.
 * @param {{basisRevisionNo: number|null, preview: boolean, transfers: Array}} settlement 정산
 * @param {Map<number, object>} directory 이름표
 * @returns {string} 문구
 */
function transferText(settlement, directory) {
	const plain = (id) => directory.get(id)?.nickname ?? `#${id}`;
	const head = settlement.preview ? '정산 미리보기' : `정산 안내(리비전 ${settlement.basisRevisionNo})`;
	const lines = settlement.transfers.map((t) => `${plain(t.fromMemberId)} → ${plain(t.toMemberId)} ${formatWon(t.amount)}`);
	return [head, ...lines].join('\n');
}

/**
 * 재확정 직후 변동액 카드(이전 확정 리비전 대비 1인당 Δ + 고정 안내 + 공유 문구).
 * @param {{previousRevisionNo: number, changes: Array<{memberId: number, previousNetBalance: number,
 *   currentNetBalance: number, delta: number}>, notice: string, shareText: string}|null} change 변동 정보
 * @param {Map<number, object>} directory 이름표
 * @returns {string} HTML
 */
function changeCard(change, directory) {
	if (!change) {
		return '';
	}
	const items = change.changes.map((c) => html`<li><div class="list-row">
		<span class="row-body"><span class="row-title">${raw(nameOf(directory, c.memberId))}</span>
			<span class="row-sub">${formatWon(c.previousNetBalance)} → ${formatWon(c.currentNetBalance)}</span></span>
		<span class="amount ${c.delta >= 0 ? 'plus' : 'minus'}">${c.delta > 0 ? '+' : ''}${formatWon(c.delta)}</span>
	</div></li>`);
	return html`<article><div class="card-head"><h4>🔄 리비전 ${change.previousRevisionNo} 대비 변동</h4></div>
		${raw(items.length ? html`<ul class="list">${raw(items.join(''))}</ul>` : html`<p class="empty">변동 없음</p>`)}
		<p class="muted"><small>${change.notice}</small></p>
		<button type="button" class="secondary block-button" data-copy="${change.shareText}">💬 변동 안내 카톡 문구 복사</button></article>`;
}

/**
 * 경고 목록을 한 줄씩 글로 바꾼다.
 * @param {Array<{type: string, expenseId: number}>|null} warnings 경고
 * @returns {string} 안내 글
 */
export function warningText(warnings) {
	const labels = {FIXED_AMOUNT_OVERFLOW: '고정 금액·벌칙 합이 지출보다 커요', NO_SHARE_TARGET: '나눌 사람이 없는 지출이 있어요'};
	return (warnings ?? []).map((w) => `- ${labels[w.type] ?? w.type} (지출 #${w.expenseId})`).join('\n');
}
