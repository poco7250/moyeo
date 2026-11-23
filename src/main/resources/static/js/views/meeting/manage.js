/* 모임 관리(호스트 권한자만): 게스트(G-6), 다른 멤버 응답 대신 입력(M-4), 호스트 위임(G-4) */

import {api} from '../../api.js';
import {formValues, memberName, runAction} from '../../common.js';
import {html, raw, toast} from '../../ui.js';

const STATUS_LABELS = {ATTEND: '참석', ABSENT: '불참', UNDECIDED: '미정'};

/**
 * 관리 영역을 그린다. 호스트 권한이 없으면 비운다.
 * @param {HTMLElement} box 관리 영역
 * @param {{meetingId: string, me: {id: number}, detail: object}} ctx 모임 화면 상태
 * @returns {Promise<void>}
 */
export async function mountManage(box, ctx) {
	if (!ctx.detail.hostAuthority) {
		box.innerHTML = '';
		return;
	}
	const members = await api.get(`/api/groups/${ctx.detail.groupId}/members`);
	box.innerHTML = html`<details class="manage-area"><summary>모임 관리(호스트)</summary>
		${raw(guestSection(ctx.detail.guests))}
		${raw(respondForForm(members, ctx.detail.rounds))}
		${raw(hostForm(members, ctx.detail.hostMemberId))}
	</details>`;
	bindManage(box.querySelector('.manage-area'), ctx);
}

/**
 * 게스트 목록과 추가 폼(이름만, 이 모임 게임에만 참가).
 * @param {Array<{guestId: number, name: string}>} guests 게스트
 * @returns {string} HTML
 */
function guestSection(guests) {
	const items = guests.map((guest) => html`<li>${guest.name}
		<button type="button" class="outline secondary" data-remove-guest="${guest.guestId}">빼기</button></li>`);
	return html`<h4>게스트</h4>
		<p class="muted"><small>카카오 계정 없는 친구를 이름만으로 넣어요. 이 모임 게임에만 참가하고 정산에는 안 들어가요.</small></p>
		<ul>${raw(items.join('') || html`<li class="muted">없음</li>`)}</ul>
		<form class="guest-form" role="group"><input name="name" placeholder="게스트 이름" maxlength="20" required>
			<button type="submit" class="secondary">추가</button></form>`;
}

/**
 * 다른 멤버 응답 대신 입력 폼.
 * @param {Array<{memberId: number, nickname: string, emoji: string|null}>} members 그룹 멤버
 * @param {Array<{id: number, name: string}>} rounds 차수
 * @returns {string} HTML
 */
function respondForForm(members, rounds) {
	const options = members.map((member) => html`<option value="${member.memberId}">${raw(memberName(member))}</option>`);
	const roundRows = rounds.map((round) => html`<label>${round.name} <select name="r${round.id}">
		<option value="">그대로</option>
		${raw(Object.entries(STATUS_LABELS).map(([value, label]) => html`<option value="${value}">${label}</option>`).join(''))}
	</select></label>`);
	return html`<h4>대신 응답하기</h4><form class="respond-for-form">
		<select name="memberId">${raw(options.join(''))}</select>${raw(roundRows.join(''))}
		<button type="submit" class="secondary">대신 응답</button></form>`;
}

/**
 * 호스트 위임 폼(나 말고 다른 멤버 중 고른다).
 * @param {Array<{memberId: number, nickname: string, emoji: string|null}>} members 그룹 멤버
 * @param {number} hostId 지금 호스트 id
 * @returns {string} HTML
 */
function hostForm(members, hostId) {
	const options = members.filter((member) => member.memberId !== hostId)
		.map((member) => html`<option value="${member.memberId}">${raw(memberName(member))}</option>`);
	if (options.length === 0) {
		return '';
	}
	return html`<h4>호스트 넘기기</h4><form class="host-form" role="group">
		<select name="memberId">${raw(options.join(''))}</select>
		<button type="submit" class="secondary">넘기기</button></form>`;
}

/**
 * 관리 영역 이벤트를 단다. 바뀐 결과는 모임 토픽 이벤트로 다시 그려진다.
 * @param {HTMLElement} area 관리 영역 안쪽 요소
 * @param {{meetingId: string, detail: object}} ctx 모임 화면 상태
 */
function bindManage(area, ctx) {
	const base = `/api/meetings/${ctx.meetingId}`;
	area.querySelector('.guest-form').addEventListener('submit', (event) => runAction(event, async () => {
		await api.post(`${base}/guests`, formValues(event.target));
		event.target.reset();
	}));
	area.addEventListener('click', (event) => {
		const guestId = event.target.closest('[data-remove-guest]')?.dataset.removeGuest;
		if (guestId && window.confirm('게스트를 뺄까요?')) {
			runAction(event, () => api.delete(`${base}/guests/${guestId}`));
		}
	});
	area.querySelector('.respond-for-form').addEventListener('submit', (event) => runAction(event, async () => {
		const form = event.target;
		const rounds = ctx.detail.rounds.map((round) => ({roundId: round.id, status: form.elements[`r${round.id}`].value}))
			.filter((item) => item.status);
		await api.put(`${base}/attendances/${form.elements.memberId.value}`, {rounds});
		toast('대신 응답했어요');
	}));
	area.querySelector('.host-form')?.addEventListener('submit', (event) => runAction(event, async () => {
		if (window.confirm('호스트를 넘길까요? 넘기면 호스트 버튼이 사라질 수 있어요.')) {
			await api.put(`${base}/host`, {memberId: Number(event.target.elements.memberId.value)});
		}
	}));
}
