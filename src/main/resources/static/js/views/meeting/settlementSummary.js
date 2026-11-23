/* 정산 요약: 1인당 부담액·순잔액, 경고, 송금 안내(받는 사람 송금 정보·카톡 문구), 재확정 변동액 카드 */

import {copyText, formatWon, nameOf, safeLink} from '../../common.js';
import {html, raw} from '../../ui.js';

/**
 * 정산 요약을 그린다.
 * @param {HTMLElement} box 요약 영역
 * @param {{ctx: object, directory: Map, settlement: object}} state 탭 상태
 */
export function renderSummary(box, state) {
	const {settlement, directory} = state;
	const rows = settlement.members.map((m) => html`<tr><td>${raw(nameOf(directory, m.memberId))}</td>
		<td>${formatWon(m.shareAmount)}</td><td>${m.netBalance >= 0 ? '받을 돈' : '낼 돈'} ${formatWon(Math.abs(m.netBalance))}</td></tr>`);
	box.innerHTML = html`<div class="summary-area">
		${raw(changeCard(settlement.revisionChange, directory))}
		<h4>1인당 금액</h4>
		${raw(rows.length ? html`<table><thead><tr><th>멤버</th><th>부담액</th><th>순잔액</th></tr></thead><tbody>${raw(rows.join(''))}</tbody></table>`
		: html`<p class="muted">아직 지출이 없어요</p>`)}
		${raw(settlement.warnings.length ? html`<p><mark>${warningText(settlement.warnings)}</mark></p>` : '')}
		<h4>송금 안내 <small class="muted">(송금은 토스·카카오페이 등 앱 밖에서 해요)</small></h4>
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
		return html`<li>${raw(nameOf(directory, t.fromMemberId))} → ${raw(nameOf(directory, t.toMemberId))}
			<strong>${formatWon(t.amount)}</strong>${raw(payeeLinks(payee))}</li>`;
	});
	return html`<ul>${raw(items.join(''))}</ul>
		<button type="button" class="secondary" data-copy="transfers">송금 안내 카톡 문구 복사</button>`;
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
	return html`<br><small>
		${raw(safeLink(payee.tossLink, '토스'))}
		${raw(safeLink(payee.kakaoPayLink, '카카오페이'))}
		${raw(account ? html`<button type="button" class="outline" data-copy="${account}">계좌 복사</button>` : '')}</small>`;
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
	const items = change.changes.map((c) => html`<li>${raw(nameOf(directory, c.memberId))}
		${c.delta > 0 ? '+' : ''}${formatWon(c.delta)} <small class="muted">(${formatWon(c.previousNetBalance)} → ${formatWon(c.currentNetBalance)})</small></li>`);
	return html`<article><header>리비전 ${change.previousRevisionNo} 대비 변동</header>
		<ul>${raw(items.join('') || html`<li>변동 없음</li>`)}</ul>
		<p class="muted">${change.notice}</p>
		<button type="button" class="secondary" data-copy="${change.shareText}">변동 안내 카톡 문구 복사</button></article>`;
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
