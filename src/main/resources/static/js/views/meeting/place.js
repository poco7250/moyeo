/*
 * 장소 탭(P-1~P-6, P-8). 차수마다 카드 하나: 데이터 영역(실시간으로 다시 그림)과 입력 폼(상태가 바뀔 때만 다시 그림).
 * 장소 검색 결과는 보여 주기만 하고 입력칸을 채우지 않는다(오픈 이슈 5, 카카오 데이터 무저장).
 */

import {api} from '../../api.js';
import {formValues, runAction} from '../../common.js';
import {html, raw, toast} from '../../ui.js';
import {roundData} from './placeRound.js';
import {mountPlaceSearch} from './placeSearch.js';

const EVENTS = new Set(['VOTE_CHANGED', 'PLACE_CHANGED', 'PLACE_DECISION_REQUIRED', 'PLACE_ROULETTE_SPUN']);

/**
 * 장소 탭을 그린다.
 * @param {HTMLElement} container 탭 본문
 * @param {{detail: object, me: {id: number}}} ctx 모임 화면 상태
 * @returns {Promise<{events: Set<string>, refresh: Function}>} 실시간 갱신 핸들
 */
export async function mountPlaceTab(container, ctx) {
	const state = {ctx, saved: await loadSaved(ctx.detail.groupId), lastStatus: new Map()};
	container.innerHTML = html`<div class="place-tab">
		${raw(ctx.detail.rounds.map((round) => html`
			<article data-round="${round.id}">
				<h3>${round.name}</h3>
				<div class="round-data"></div>
				<div class="round-forms"></div>
			</article>`).join(''))}
		<section class="place-search"></section>
	</div>`;
	const tab = container.querySelector('.place-tab');
	mountPlaceSearch(tab.querySelector('.place-search'));
	const refreshAll = () => Promise.all(ctx.detail.rounds.map((round) => refreshRound(tab, state, round.id)));
	await refreshAll();
	return {
		events: EVENTS,
		refresh: (message) => {
			if (!message || !EVENTS.has(message.type)) {
				// 모임 단위 변경(호스트 위임 등)·재연결이면 권한이 바뀌었을 수 있어 폼도 다시 그린다
				state.lastStatus.clear();
			}
			const roundId = message?.data?.roundId;
			return roundId && tab.querySelector(`[data-round="${roundId}"]`) ? refreshRound(tab, state, roundId) : refreshAll();
		},
	};
}

/**
 * 그룹 단골·지난 장소를 받는다(불러오기용, 사용자 입력 값이라 폼에 채워도 된다).
 * @param {number} groupId 그룹 id
 * @returns {Promise<{favorites: Array, recent: Array}>} 저장된 장소
 */
async function loadSaved(groupId) {
	const [favorites, recent] = await Promise.all([
		api.get(`/api/groups/${groupId}/favorite-places`), api.get(`/api/groups/${groupId}/recent-places`)]);
	return {favorites, recent};
}

/**
 * 차수 카드 하나를 다시 받는다. 장소 상태가 바뀌었으면 입력 폼도 다시 그린다.
 * @param {HTMLElement} tab 장소 탭
 * @param {{ctx: object, saved: object, lastStatus: Map}} state 탭 상태
 * @param {number} roundId 차수 id
 * @returns {Promise<void>}
 */
async function refreshRound(tab, state, roundId) {
	const card = tab.querySelector(`[data-round="${roundId}"]`);
	const place = await api.get(`/api/rounds/${roundId}/place`);
	const votes = place.placeStatus === 'VOTING' ? await api.get(`/api/rounds/${roundId}/votes`) : null;
	const data = card.querySelector('.round-data');
	data.innerHTML = roundData(place, votes, state.ctx);
	bindRoundData(data.firstElementChild, roundId, () => refreshRound(tab, state, roundId));
	const statusKey = `${place.placeStatus}:${place.hostDecisionRequired}`;
	if (state.lastStatus.get(roundId) !== statusKey) {
		state.lastStatus.set(roundId, statusKey);
		renderForms(card.querySelector('.round-forms'), place, state, () => refreshRound(tab, state, roundId));
	}
}

/**
 * 데이터 영역의 버튼(투표·취소·호스트 확정·룰렛)을 처리한다.
 * @param {HTMLElement} area 데이터 영역 안쪽 요소
 * @param {number} roundId 차수 id
 * @param {() => Promise<void>} reload 다시 그리기
 */
function bindRoundData(area, roundId, reload) {
	const base = `/api/rounds/${roundId}`;
	area.addEventListener('click', (event) => {
		const button = event.target.closest('button[data-act]');
		if (!button) {
			return;
		}
		const {act, candidate} = button.dataset;
		runAction(event, async () => {
			if (act === 'vote' || act === 'unvote') {
				await api[act === 'vote' ? 'put' : 'delete'](`${base}/candidates/${candidate}/vote`);
			} else if (act === 'confirm' && window.confirm('이 후보로 확정할까요?')) {
				await api.post(`${base}/place/confirm`, {candidateId: Number(candidate)});
			} else if (act === 'roulette' && window.confirm('동점 후보 중에서 룰렛으로 정할까요?')) {
				await api.post(`${base}/place/roulette`);
			}
			await reload();
		});
	});
}

/**
 * 입력 폼: 후보 추가(누구나)·바로 확정(호스트, 미정일 때)·투표 열기(호스트, 미정일 때). 확정이면 비운다.
 * @param {HTMLElement} box 폼 영역
 * @param {{placeStatus: string}} place 차수 장소 상태
 * @param {{ctx: object, saved: object}} state 탭 상태
 * @param {() => Promise<void>} reload 다시 그리기
 */
function renderForms(box, place, state, reload) {
	if (place.placeStatus === 'CONFIRMED') {
		box.innerHTML = '';
		return;
	}
	const hostUndecided = state.ctx.detail.hostAuthority && place.placeStatus === 'UNDECIDED';
	box.innerHTML = html`<div class="round-form-area">
		<details><summary>장소 후보 넣기</summary>${raw(candidateForm(state.saved, hostUndecided))}</details>
		${raw(hostUndecided ? votingForm() : '')}
	</div>`;
	bindForms(box.querySelector('.round-form-area'), place.roundId, state, reload);
}

/**
 * 후보 입력 폼. 단골·지난 장소를 골라 채울 수 있다.
 * @param {{favorites: Array, recent: Array}} saved 저장된 장소
 * @param {boolean} canConfirm 바로 확정 버튼을 보일지
 * @returns {string} HTML
 */
function candidateForm(saved, canConfirm) {
	const option = (place, key) => html`<option value="${key}">${place.label}${place.manualAddress ? ` · ${place.manualAddress}` : ''}</option>`;
	return html`<form class="candidate-form">
		<select name="saved"><option value="">단골·지난 장소에서 불러오기</option>
			${raw(saved.favorites.map((p, i) => option(p, `f${i}`)).join(''))}
			${raw(saved.recent.map((p, i) => option(p, `r${i}`)).join(''))}</select>
		<input name="label" placeholder="장소 이름(직접 입력)" maxlength="50" required>
		<input name="manualAddress" placeholder="주소(선택)" maxlength="200">
		<input name="manualMapUrl" type="url" placeholder="지도 링크 https://(선택)">
		<button type="submit" name="mode" value="candidate">후보 추가</button>
		${raw(canConfirm ? html`<button type="submit" name="mode" value="direct" class="secondary">바로 확정</button>` : '')}
		<button type="button" class="outline" data-favorite>단골로 저장</button>
	</form>`;
}

/**
 * 호스트용 투표 열기 폼.
 * @returns {string} HTML
 */
function votingForm() {
	return html`<form class="voting-form" role="group">
		<input name="placeVoteDeadline" type="datetime-local" required aria-label="투표 마감">
		<button type="submit" class="secondary">투표 열기</button>
	</form>`;
}

/**
 * 입력 폼 이벤트를 단다.
 * @param {HTMLElement} area 폼 영역 안쪽 요소
 * @param {number} roundId 차수 id
 * @param {{ctx: object, saved: object}} state 탭 상태
 * @param {() => Promise<void>} reload 다시 그리기
 */
function bindForms(area, roundId, state, reload) {
	const base = `/api/rounds/${roundId}`;
	const form = area.querySelector('.candidate-form');
	form.elements.saved.addEventListener('change', () => fillFromSaved(form, state.saved));
	form.addEventListener('submit', (event) => runAction(event, async () => {
		const direct = event.submitter?.value === 'direct';
		await api.post(direct ? `${base}/place/confirm-direct` : `${base}/candidates`, placeInput(form));
		form.reset();
		toast(direct ? '장소를 확정했어요' : '후보를 넣었어요');
		await reload();
	}));
	form.querySelector('[data-favorite]').addEventListener('click', (event) => runAction(event, async () => {
		await api.post(`/api/groups/${state.ctx.detail.groupId}/favorite-places`, placeInput(form));
		state.saved = await loadSaved(state.ctx.detail.groupId);
		toast('단골 장소로 저장했어요');
	}));
	area.querySelector('.voting-form')?.addEventListener('submit', (event) => runAction(event, async () => {
		const deadline = event.target.elements.placeVoteDeadline.value;
		await api.post(`${base}/place/voting`, {placeVoteDeadline: `${deadline}:00`});
		await reload();
	}));
}

/**
 * 고른 단골·지난 장소로 입력칸을 채운다(사용자가 전에 직접 입력한 값).
 * @param {HTMLFormElement} form 후보 폼
 * @param {{favorites: Array, recent: Array}} saved 저장된 장소
 */
function fillFromSaved(form, saved) {
	const key = form.elements.saved.value;
	const place = key.startsWith('f') ? saved.favorites[Number(key.slice(1))] : saved.recent[Number(key.slice(1))];
	if (!place) {
		return;
	}
	form.elements.label.value = place.label;
	form.elements.manualAddress.value = place.manualAddress ?? '';
	form.elements.manualMapUrl.value = place.manualMapUrl ?? '';
}

/**
 * 후보 폼을 서버 PlaceInput으로 바꾼다.
 * @param {HTMLFormElement} form 후보 폼
 * @returns {{label: string, manualAddress: string, manualMapUrl: string}} 요청 본문
 */
function placeInput(form) {
	const {label, manualAddress, manualMapUrl} = formValues(form);
	return {label, manualAddress, manualMapUrl};
}
