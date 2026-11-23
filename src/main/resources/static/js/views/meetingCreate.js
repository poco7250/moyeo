/* 모임 만들기(M-1, M-2, M-5): 제목·메모·응답 마감, 차수 템플릿 또는 직접 입력, 날짜 확정 또는 후보 투표 */

import {api} from '../api.js';
import {findGroup, fromLocalInput, groupHeader, notMemberNotice} from '../common.js';
import {navigate} from '../router.js';
import {html, raw, render, showError} from '../ui.js';

const TEMPLATES = {
	MEAL_ONLY: '밥',
	MEAL_BOWLING: '밥 → 볼링',
	MEAL_BOARD_GAME: '밥 → 보드게임',
	MEAL_BOWLING_BOARD_GAME: '밥 → 볼링 → 보드게임',
};
const CUSTOM = 'CUSTOM';

/**
 * 모임 만들기 폼을 그린다.
 * @param {{params: {groupId: string}}} ctx 라우터 컨텍스트
 * @returns {Promise<void>}
 */
export async function meetingCreateView({params}) {
	const group = await findGroup(params.groupId);
	if (!group) {
		render(notMemberNotice());
		return;
	}
	const form = render(html`
		${raw(groupHeader(group, '모임 만들기'))}
		<form id="meeting-create">
			<label>제목 <input name="title" maxlength="100" required></label>
			<label>메모 <textarea name="memo" maxlength="1000" rows="2"></textarea></label>
			${raw(roundFields())}
			${raw(dateFields())}
			<label>응답 마감(선택) <input name="responseDeadline" type="datetime-local"></label>
			<button type="submit">만들기</button>
		</form>
	`).querySelector('#meeting-create');
	form.addEventListener('change', () => syncVisibility(form));
	form.querySelector('#add-candidate').addEventListener('click', () => addCandidateInput(form));
	form.addEventListener('submit', (event) => onSubmit(event, group.id));
	syncVisibility(form);
}

/**
 * 차수 선택 영역(템플릿 또는 직접 입력).
 * @returns {string} HTML
 */
function roundFields() {
	const options = Object.entries(TEMPLATES).map(([value, label]) => html`<option value="${value}">${label}</option>`);
	return html`
		<label>차수 <select name="template">${raw(options.join(''))}<option value="${CUSTOM}">직접 입력</option></select></label>
		<label data-show="custom-rounds">차수 이름(쉼표로 구분, 5개까지) <input name="rounds" placeholder="밥, 카페, 노래방"></label>
	`;
}

/**
 * 날짜 영역(바로 확정 또는 후보 투표).
 * @returns {string} HTML
 */
function dateFields() {
	return html`
		<fieldset>
			<legend>날짜</legend>
			<label><input type="radio" name="dateMode" value="fixed" checked> 정해졌어요</label>
			<label><input type="radio" name="dateMode" value="vote"> 후보를 두고 투표할래요</label>
		</fieldset>
		<label data-show="fixed">날짜·시간 <input name="startAt" type="datetime-local"></label>
		<div data-show="vote" id="date-candidates">
			<label>후보 1 <input name="dateCandidate" type="datetime-local"></label>
			<label>후보 2 <input name="dateCandidate" type="datetime-local"></label>
			<button type="button" id="add-candidate" class="secondary">후보 추가</button>
		</div>
	`;
}

/**
 * 고른 방식에 맞는 입력칸만 보여 준다.
 * @param {HTMLFormElement} form 폼
 */
function syncVisibility(form) {
	const custom = form.elements.template.value === CUSTOM;
	const vote = form.querySelector('input[name="dateMode"]:checked').value === 'vote';
	form.querySelector('[data-show="custom-rounds"]').hidden = !custom;
	form.querySelector('[data-show="fixed"]').hidden = vote;
	form.querySelector('[data-show="vote"]').hidden = !vote;
}

/**
 * 날짜 후보 입력칸을 하나 더한다(서버가 10개까지 받는다).
 * @param {HTMLFormElement} form 폼
 */
function addCandidateInput(form) {
	const count = form.querySelectorAll('input[name="dateCandidate"]').length;
	const label = document.createElement('label');
	label.innerHTML = html`후보 ${count + 1} <input name="dateCandidate" type="datetime-local">`;
	form.querySelector('#add-candidate').before(label);
}

/**
 * 폼 값을 서버 요청 형식으로 바꿔 보내고 모임 상세로 간다.
 * @param {SubmitEvent} event 제출 이벤트
 * @param {number} groupId 그룹 id
 * @returns {Promise<void>}
 */
async function onSubmit(event, groupId) {
	event.preventDefault();
	try {
		const meeting = await api.post(`/api/groups/${groupId}/meetings`, toRequest(event.target));
		navigate(`/meetings/${meeting.id}`);
	} catch (error) {
		showError(error);
	}
}

/**
 * 폼을 MeetingCreateRequest로 바꾼다. 고르지 않은 쪽 값은 보내지 않는다.
 * @param {HTMLFormElement} form 폼
 * @returns {object} 요청 본문
 */
function toRequest(form) {
	const custom = form.elements.template.value === CUSTOM;
	const vote = form.querySelector('input[name="dateMode"]:checked').value === 'vote';
	const candidates = [...form.querySelectorAll('input[name="dateCandidate"]')]
		.map((input) => fromLocalInput(input.value)).filter(Boolean);
	return {
		title: form.elements.title.value,
		memo: form.elements.memo.value || null,
		responseDeadline: fromLocalInput(form.elements.responseDeadline.value),
		template: custom ? null : form.elements.template.value,
		rounds: custom ? form.elements.rounds.value.split(',').map((name) => name.trim()).filter(Boolean) : null,
		startAt: vote ? null : fromLocalInput(form.elements.startAt.value),
		dateCandidates: vote ? candidates : null,
	};
}
