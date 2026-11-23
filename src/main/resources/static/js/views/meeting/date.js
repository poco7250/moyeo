/*
 * 날짜 후보 투표(M-5). 모임이 날짜 투표 중일 때만 보인다.
 * 다른 사람 투표로 다시 그려도, 아직 보내지 않은 내 선택(dirty)은 유지한다.
 */

import {api} from '../../api.js';
import {formatDate, fromLocalInput, memberName, runAction as run} from '../../common.js';
import {html, raw, toast} from '../../ui.js';

/**
 * 날짜 투표 영역을 그린다. 투표 중이 아니면 비운다.
 * @param {HTMLElement} container 그릴 곳(#meeting-date)
 * @param {{meetingId: string, me: {id: number}, detail: object}} ctx 모임 화면 상태
 * @returns {Promise<void>}
 */
export async function renderDateVote(container, ctx) {
	if (ctx.detail.dateStatus !== 'VOTING') {
		container.innerHTML = '';
		return;
	}
	const votes = await api.get(`/api/meetings/${ctx.meetingId}/date-votes`);
	const kept = keptSelection(container);
	container.innerHTML = html`<article class="date-area">
		<h3>날짜 투표</h3>
		<form class="date-mine">${raw(votes.candidates.map((c) => candidateRow(c, ctx, kept)).join(''))}
			<button type="submit">내 가능 날짜 보내기</button>
		</form>
		${raw(ctx.detail.hostAuthority ? addCandidateForm() : '')}
	</article>`;
	bindDateArea(container.querySelector('.date-area'), ctx, () => renderDateVote(container, ctx));
}

/**
 * 보내지 않은 내 선택이 있으면 그 후보 id 목록을 돌려준다.
 * @param {HTMLElement} container 날짜 영역
 * @returns {Set<string>|null} 체크한 후보 id(바꾼 적 없으면 null)
 */
function keptSelection(container) {
	const form = container.querySelector('.date-mine');
	if (!form?.dataset.dirty) {
		return null;
	}
	return new Set([...form.querySelectorAll('input:checked')].map((input) => input.value));
}

/**
 * 후보 한 줄: 내 체크박스, 가능 인원·명단, 호스트면 확정 버튼.
 * @param {{candidateId: number, startAt: string, availableCount: number, available: Array, unanswered: Array}} candidate 후보
 * @param {{me: {id: number}, detail: object}} ctx 모임 화면 상태
 * @param {Set<string>|null} kept 유지할 내 선택
 * @returns {string} HTML
 */
function candidateRow(candidate, ctx, kept) {
	const mine = kept ? kept.has(String(candidate.candidateId))
		: candidate.available.some((member) => member.memberId === ctx.me.id);
	return html`
		<label>
			<input type="checkbox" value="${candidate.candidateId}" ${raw(mine ? 'checked' : '')}>
			<strong>${formatDate(candidate.startAt)}</strong> — 가능 ${candidate.availableCount}명
			<small class="muted">${raw(candidate.available.map(memberName).join(', '))}</small>
			${raw(ctx.detail.hostAuthority ? html`<button type="button" class="outline" data-confirm="${candidate.candidateId}">이 날짜로 확정</button>` : '')}
		</label>
	`;
}

/**
 * 호스트용 후보 추가 폼.
 * @returns {string} HTML
 */
function addCandidateForm() {
	return html`<form class="date-add" role="group">
		<input name="startAt" type="datetime-local" required>
		<button type="submit" class="secondary">후보 추가</button>
	</form>`;
}

/**
 * 날짜 영역 이벤트를 단다(영역은 매번 새로 그려지므로 중복되지 않는다).
 * @param {HTMLElement} area 날짜 영역
 * @param {{meetingId: string}} ctx 모임 화면 상태
 * @param {() => Promise<void>} reload 다시 그리기
 */
function bindDateArea(area, ctx, reload) {
	const base = `/api/meetings/${ctx.meetingId}`;
	const mine = area.querySelector('.date-mine');
	mine.addEventListener('change', () => {
		mine.dataset.dirty = '1';
	});
	mine.addEventListener('submit', (event) => run(event, async () => {
		const ids = [...mine.querySelectorAll('input:checked')].map((input) => Number(input.value));
		await api.put(`${base}/date-votes/me`, {availableCandidateIds: ids});
		delete mine.dataset.dirty;
		toast('보냈어요');
		await reload();
	}));
	mine.addEventListener('click', (event) => {
		const id = event.target.closest('[data-confirm]')?.dataset.confirm;
		if (id && window.confirm('이 날짜로 확정할까요?')) {
			run(event, () => api.post(`${base}/date/confirm`, {candidateId: Number(id)}));
		}
	});
	area.querySelector('.date-add')?.addEventListener('submit', (event) => run(event, async () => {
		await api.post(`${base}/date-candidates`, {startAt: fromLocalInput(event.target.elements.startAt.value)});
		await reload();
	}));
}
