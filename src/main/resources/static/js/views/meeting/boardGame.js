/* 보드게임 승패 기록(R-7): 차수별 목록, 만들기·수정(순위·꼴등), 삭제(기록한 사람 또는 호스트). 정산과는 연결 없음 */

import {api} from '../../api.js';
import {nameOf, runAction} from '../../common.js';
import {html, raw, toast} from '../../ui.js';

/**
 * 보드게임 영역을 그린다.
 * @param {HTMLElement} box 보드게임 영역
 * @param {{rounds: Array<{roundId: number, name: string, attend: Array}>}} board 참석 현황판(참가자 후보)
 * @param {{ctx: object, directory: Map}} state 게임 탭 상태
 * @returns {Promise<{refresh: () => Promise<void>}>} 다시 그리기 핸들
 */
export async function mountBoardGames(box, board, state) {
	box.innerHTML = html`<div class="board-game-area"><h3 class="band">보드게임 기록</h3>
		<div class="board-game-list"></div>
		<details class="board-game-editor"><summary>✏️ 기록 남기기</summary>${raw(recordForm(board))}</details>
	</div>`;
	const area = box.querySelector('.board-game-area');
	const form = area.querySelector('.board-game-form');
	const refresh = () => renderList(area.querySelector('.board-game-list'), board, state, form, refresh);
	syncRankInputs(form, board);
	form.elements.roundId.addEventListener('change', () => syncRankInputs(form, board));
	form.addEventListener('submit', (event) => runAction(event, async () => {
		await saveRecord(form);
		resetForm(form, board);
		await refresh();
	}));
	await refresh();
	return {refresh};
}

/**
 * 차수별 기록 목록을 다시 받는다.
 * @param {HTMLElement} list 목록 영역
 * @param {{rounds: Array}} board 참석 현황판
 * @param {{ctx: object, directory: Map}} state 게임 탭 상태
 * @param {HTMLFormElement} form 기록 폼(수정할 때 채움)
 * @param {() => Promise<void>} reload 다시 그리기
 * @returns {Promise<void>}
 */
async function renderList(list, board, state, form, reload) {
	const perRound = await Promise.all(board.rounds.map((r) => api.get(`/api/rounds/${r.roundId}/board-games`)));
	const records = perRound.flat();
	list.innerHTML = html`<div class="board-game-rows">${raw(records.length
		? html`<ul class="list">${raw(records.map((record) => recordRow(record, state)).join(''))}</ul>`
		: html`<p class="empty">기록이 없어요</p>`)}</div>`;
	list.querySelector('.board-game-rows').addEventListener('click', (event) => {
		const button = event.target.closest('button[data-record]');
		const record = records.find((r) => String(r.id) === button?.dataset.record);
		if (!record) {
			return;
		}
		if (button.dataset.act === 'edit') {
			fillForm(form, board, record);
			return;
		}
		if (window.confirm('이 기록을 지울까요?')) {
			runAction(event, async () => {
				await api.delete(`/api/board-games/${record.id}`);
				await reload();
			});
		}
	});
}

/**
 * 기록 한 줄: 게임 이름, 순위, 꼴등, 수정·삭제 버튼(기록한 사람 또는 호스트).
 * @param {{id: number, gameName: string, ranks: Array<{memberId: number, rank: number}>, loserMemberId: number,
 *   recordedByMemberId: number}} record 기록
 * @param {{ctx: object, directory: Map}} state 게임 탭 상태
 * @returns {string} HTML
 */
function recordRow(record, state) {
	const ranks = [...record.ranks].sort((a, b) => a.rank - b.rank)
		.map((r) => `${r.rank}위 ${nameOf(state.directory, r.memberId)}`).join(' · ');
	const editable = record.recordedByMemberId === state.ctx.me.id || state.ctx.detail.hostAuthority;
	return html`<li><div class="list-row">
		<span class="icon-tile">🧩</span>
		<span class="row-body"><span class="row-title">${record.gameName}
			<span class="chip danger">꼴등 ${raw(nameOf(state.directory, record.loserMemberId))}</span></span>
			<span class="row-sub">${raw(ranks)}</span>
			${raw(editable ? html`<span class="row-actions"><button type="button" class="secondary" data-act="edit" data-record="${record.id}">수정</button>
				<button type="button" class="secondary" data-act="delete" data-record="${record.id}">삭제</button></span>` : '')}</span>
	</div></li>`;
}

/**
 * 기록 폼(새로 만들기·수정 겸용, recordId가 있으면 수정).
 * @param {{rounds: Array<{roundId: number, name: string}>}} board 참석 현황판
 * @returns {string} HTML
 */
function recordForm(board) {
	return html`<form class="board-game-form">
		<input type="hidden" name="recordId">
		<label>차수 <select name="roundId">${raw(board.rounds.map((r) => html`<option value="${r.roundId}">${r.name}</option>`).join(''))}</select></label>
		<label>게임 이름 <input name="gameName" maxlength="50" required placeholder="예: 스플렌더"></label>
		<p class="muted"><small>참가한 사람만 순위를 적어요(같은 순위 가능).</small></p>
		<div class="rank-inputs"></div>
		<label>꼴등 <select name="loserMemberId" required></select></label>
		<button type="submit" class="block-button">저장</button>
	</form>`;
}

/**
 * 고른 차수 참석자로 순위 입력칸과 꼴등 선택지를 만든다.
 * @param {HTMLFormElement} form 기록 폼
 * @param {{rounds: Array<{roundId: number, attend: Array}>}} board 참석 현황판
 */
function syncRankInputs(form, board) {
	const round = board.rounds.find((r) => String(r.roundId) === form.elements.roundId.value);
	const people = round?.attend ?? [];
	const label = (m) => `${m.emoji ? `${m.emoji} ` : ''}${m.nickname}`;
	form.querySelector('.rank-inputs').innerHTML = people.map((m) => html`<label>${label(m)}
		<input type="number" min="1" name="rank" data-member="${m.memberId}" placeholder="순위"></label>`).join('')
		|| html`<p class="muted">이 차수 참석자가 없어요</p>`;
	form.elements.loserMemberId.innerHTML = people.map((m) => html`<option value="${m.memberId}">${label(m)}</option>`).join('');
}

/**
 * 수정할 기록으로 폼을 채운다.
 * @param {HTMLFormElement} form 기록 폼
 * @param {{rounds: Array}} board 참석 현황판
 * @param {{id: number, roundId: number, gameName: string, ranks: Array, loserMemberId: number}} record 기록
 */
function fillForm(form, board, record) {
	form.closest('details').open = true;
	form.elements.recordId.value = record.id;
	form.elements.roundId.value = record.roundId;
	syncRankInputs(form, board);
	form.elements.gameName.value = record.gameName;
	record.ranks.forEach((r) => {
		const input = form.querySelector(`input[data-member="${r.memberId}"]`);
		if (input) {
			input.value = r.rank;
		}
	});
	form.elements.loserMemberId.value = record.loserMemberId;
}

/**
 * 폼 값을 저장한다(recordId가 있으면 수정).
 * @param {HTMLFormElement} form 기록 폼
 * @returns {Promise<void>}
 */
async function saveRecord(form) {
	const ranks = [...form.querySelectorAll('input[name="rank"]')].filter((input) => input.value)
		.map((input) => ({memberId: Number(input.dataset.member), rank: Number(input.value)}));
	const body = {gameName: form.elements.gameName.value, ranks, loserMemberId: Number(form.elements.loserMemberId.value)};
	const recordId = form.elements.recordId.value;
	await (recordId ? api.put(`/api/board-games/${recordId}`, body)
		: api.post(`/api/rounds/${form.elements.roundId.value}/board-games`, body));
	toast('저장했어요');
}

/**
 * 폼을 새로 만들기 상태로 되돌린다.
 * @param {HTMLFormElement} form 기록 폼
 * @param {{rounds: Array}} board 참석 현황판
 */
function resetForm(form, board) {
	form.reset();
	form.elements.recordId.value = '';
	syncRankInputs(form, board);
}
