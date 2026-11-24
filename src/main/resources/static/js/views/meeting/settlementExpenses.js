/*
 * 정산 탭의 지출(S-1, S-3): 차수별 목록(다시 그림)과 지출 추가 폼(한 번만 그림).
 * 지출마다 수정·삭제, 개별 조정(제외·고정 금액·가중치), 영수증 올리기·보기·삭제를 둔다.
 */

import {api} from '../../api.js';
import {formatWon, nameOf, runAction} from '../../common.js';
import {html, raw, toast} from '../../ui.js';

/**
 * 지출 영역을 만든다. 목록은 render(expenses)로 다시 그린다.
 * @param {HTMLElement} box 지출 영역
 * @param {{ctx: object, directory: Map, reload: () => Promise<void>}} state 정산 탭 상태
 * @returns {{render: (expenses: Array) => void}} 다시 그리기 핸들
 */
export function mountExpenses(box, state) {
	const rounds = state.ctx.detail.rounds;
	box.innerHTML = html`<div class="expenses-area"><h3 class="band">지출</h3>
		${raw(rounds.map((round) => html`<article data-round="${round.id}"><div class="card-head"><h4>${round.name}</h4></div>
			<div class="expense-rows"></div>
			<details><summary>➕ 지출 추가</summary>${raw(expenseForm(state, null))}</details></article>`).join(''))}
	</div>`;
	const area = box.querySelector('.expenses-area');
	area.querySelectorAll('article[data-round] > details form').forEach((form) => {
		const roundId = form.closest('article').dataset.round;
		form.addEventListener('submit', (event) => runAction(event, async () => {
			await api.post(`/api/rounds/${roundId}/expenses`, expenseBody(form));
			form.reset();
			toast('지출을 넣었어요');
			await state.reload();
		}));
	});
	return {render: (expenses) => renderRows(area, expenses, state)};
}

/**
 * 지출 입력 폼(추가·수정 겸용).
 * @param {{directory: Map}} state 정산 탭 상태
 * @param {{amount: number, payerId: number, memo: string|null}|null} expense 수정할 지출(추가면 null)
 * @returns {string} HTML
 */
function expenseForm(state, expense) {
	const payers = [...state.directory.entries()].filter(([, m]) => !m.guest).map(([id]) => html`
		<option value="${id}" ${raw(expense?.payerId === id || (!expense && id === state.ctx.me.id) ? 'selected' : '')}>${raw(nameOf(state.directory, id))}</option>`);
	return html`<form data-form="${expense ? 'edit' : 'add'}">
		<input name="amount" type="number" min="1" max="100000000" placeholder="금액(원)" value="${expense?.amount ?? ''}" required>
		<select name="payerId">${raw(payers.join(''))}</select>
		<input name="memo" maxlength="100" placeholder="메모(예: 고기)" value="${expense?.memo ?? ''}">
		<button type="submit" class="block-button">${expense ? '수정' : '추가'}</button>
	</form>`;
}

/**
 * 폼을 ExpenseRequest로 바꾼다.
 * @param {HTMLFormElement} form 지출 폼
 * @returns {{amount: number, payerId: number, memo: string|null}} 요청 본문
 */
function expenseBody(form) {
	return {amount: Number(form.elements.amount.value), payerId: Number(form.elements.payerId.value),
		memo: form.elements.memo.value || null};
}

/**
 * 차수별 지출 줄을 다시 그리고 이벤트를 단다.
 * @param {HTMLElement} area 지출 영역
 * @param {Array} expenses 지출 목록
 * @param {{directory: Map, reload: () => Promise<void>}} state 정산 탭 상태
 */
function renderRows(area, expenses, state) {
	area.querySelectorAll('article[data-round]').forEach((article) => {
		const rows = expenses.filter((e) => String(e.roundId) === article.dataset.round);
		const box = article.querySelector('.expense-rows');
		box.innerHTML = html`<div class="expense-rows-area">${raw(rows.length
			? rows.map((e) => expenseRow(e, state)).join('') : html`<p class="empty">아직 지출이 없어요</p>`)}</div>`;
		const rowsArea = box.querySelector('.expense-rows-area');
		rowsArea.addEventListener('submit', (event) => onRowSubmit(event, state));
		rowsArea.addEventListener('click', (event) => onRowClick(event, state));
	});
}

/**
 * 지출 한 줄: 금액·결제자·메모·영수증 + 접힌 관리(수정·개별 조정·영수증·삭제).
 * @param {{id: number, amount: number, payerId: number, memo: string|null, shares: Array, receipt: object|null}} expense 지출
 * @param {{directory: Map}} state 정산 탭 상태
 * @returns {string} HTML
 */
function expenseRow(expense, state) {
	const receiptUrl = expense.receipt?.url;
	const receipt = receiptUrl && receiptUrl.startsWith('/')
		? html`<a href="${receiptUrl}" target="_blank"><img class="receipt-thumb" src="${receiptUrl}" alt="영수증" loading="lazy"></a>`
		: html`<span class="icon-tile">🧾</span>`;
	return html`<div class="expense" data-expense="${expense.id}">
		<div class="list-row">
			${raw(receipt)}
			<span class="row-body"><span class="row-title">${expense.memo || '지출'}</span>
				<span class="row-sub">${raw(nameOf(state.directory, expense.payerId))} 결제</span></span>
			<span class="amount">${formatWon(expense.amount)}</span>
		</div>
		<details><summary>수정·나누기 조정·영수증</summary>
			${raw(expenseForm(state, expense))}
			${raw(sharesForm(expense, state))}
			<form data-form="receipt"><input type="file" name="file" accept="image/jpeg,image/png,image/webp" required>
				<button type="submit" class="secondary block-button">영수증 ${expense.receipt ? '교체' : '올리기'}</button></form>
			<div class="button-row">
				${raw(expense.receipt ? html`<button type="button" class="secondary" data-act="receipt-delete">영수증 삭제</button>` : '')}
				<button type="button" class="secondary danger-text" data-act="delete">지출 삭제</button>
			</div>
		</details>
	</div>`;
}

/**
 * 개별 조정 폼: 멤버별 제외·고정 금액·가중치(비우면 균등).
 * @param {{shares: Array<{memberId: number, amount: number|null, weight: number|null, excluded: boolean}>}} expense 지출
 * @param {{directory: Map}} state 정산 탭 상태
 * @returns {string} HTML
 */
function sharesForm(expense, state) {
	const rows = expense.shares.map((s) => html`<tr data-member="${s.memberId}"><td>${raw(nameOf(state.directory, s.memberId))}</td>
		<td><input type="checkbox" name="excluded" ${raw(s.excluded ? 'checked' : '')} aria-label="제외"></td>
		<td><input type="number" name="amount" min="1" value="${s.amount ?? ''}" placeholder="고정"></td>
		<td><input type="number" name="weight" min="1" max="100" value="${s.weight ?? ''}" placeholder="가중치"></td></tr>`);
	return html`<form data-form="shares"><table><thead><tr><th>멤버</th><th>제외</th><th>고정 금액</th><th>가중치</th></tr></thead>
		<tbody>${raw(rows.join(''))}</tbody></table><button type="submit" class="secondary block-button">나누기 조정 저장</button></form>`;
}

/**
 * 지출 줄 안 폼 제출(수정·개별 조정·영수증)을 처리한다.
 * @param {SubmitEvent} event 제출 이벤트
 * @param {{reload: () => Promise<void>}} state 정산 탭 상태
 */
function onRowSubmit(event, state) {
	const form = event.target;
	const id = form.closest('[data-expense]').dataset.expense;
	runAction(event, async () => {
		if (form.dataset.form === 'edit') {
			await api.put(`/api/expenses/${id}`, expenseBody(form));
		} else if (form.dataset.form === 'shares') {
			await api.put(`/api/expenses/${id}/shares`, {shares: sharesBody(form)});
		} else if (form.dataset.form === 'receipt') {
			const data = new FormData();
			data.append('file', form.elements.file.files[0]);
			await api.put(`/api/expenses/${id}/receipt`, data);
		}
		toast('저장했어요');
		await state.reload();
	});
}

/**
 * 개별 조정 폼을 ExpenseShareItem 목록으로 바꾼다.
 * @param {HTMLFormElement} form 개별 조정 폼
 * @returns {Array<{memberId: number, amount: number|null, weight: number|null, excluded: boolean}>} 조정 목록
 */
function sharesBody(form) {
	return [...form.querySelectorAll('tr[data-member]')].map((row) => ({
		memberId: Number(row.dataset.member),
		excluded: row.querySelector('[name="excluded"]').checked,
		amount: row.querySelector('[name="amount"]').value ? Number(row.querySelector('[name="amount"]').value) : null,
		weight: row.querySelector('[name="weight"]').value ? Number(row.querySelector('[name="weight"]').value) : null,
	}));
}

/**
 * 지출 줄 버튼(지출 삭제·영수증 삭제)을 처리한다.
 * @param {MouseEvent} event 클릭 이벤트
 * @param {{reload: () => Promise<void>}} state 정산 탭 상태
 */
function onRowClick(event, state) {
	const act = event.target.closest('[data-act]')?.dataset.act;
	if (!act) {
		return;
	}
	const id = event.target.closest('[data-expense]').dataset.expense;
	const question = act === 'delete' ? '지출을 삭제할까요? 이 지출이 대상인 벌칙 조건은 풀려요.' : '영수증을 삭제할까요?';
	if (!window.confirm(question)) {
		return;
	}
	runAction(event, async () => {
		const result = await api.delete(act === 'delete' ? `/api/expenses/${id}` : `/api/expenses/${id}/receipt`);
		if (result?.conditionResetGameIds?.length) {
			toast(`벌칙 조건 ${result.conditionResetGameIds.length}건이 풀렸어요. 다시 정해 주세요.`);
		}
		await state.reload();
	});
}
