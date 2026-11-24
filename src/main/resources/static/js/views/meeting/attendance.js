/*
 * 참석 탭(M-3, M-4, M-6, M-7). 현황판(실시간으로 다시 그림)과 내 응답 폼(처음과 내가 보낸 뒤에만 다시 그림)을 나눈다.
 * 노쇼(M-8)는 실제 참석자 확정 때 서버가 기록하고 히스토리·명예의 전당에서 보인다.
 */

import {ApiError, api} from '../../api.js';
import {copyText, formatDate, memberName, peopleChips, runAction, thumb} from '../../common.js';
import {html, raw, toast} from '../../ui.js';
import {mountManage} from './manage.js';

const EVENTS = new Set(['ATTENDANCE_CHANGED', 'CHECK_IN_CHANGED', 'ROUND_ATTENDANCE_CONFIRMED']);
const STATUS_LABELS = {ATTEND: '참석', ABSENT: '불참', UNDECIDED: '미정'};

/**
 * 참석 탭을 그린다.
 * @param {HTMLElement} container 탭 본문
 * @param {{meetingId: string, me: {id: number}, detail: object}} ctx 모임 화면 상태
 * @returns {Promise<{events: Set<string>, refresh: Function}>} 실시간 갱신 핸들
 */
export async function mountAttendanceTab(container, ctx) {
	container.innerHTML = html`<div class="attendance-tab">
		<section class="board"></section>
		<section class="mine"></section>
		<section class="reminder"></section>
		<section class="manage"></section>
	</div>`;
	const tab = container.querySelector('.attendance-tab');
	const refreshBoard = async () => {
		const board = await api.get(`/api/meetings/${ctx.meetingId}/attendance-board`);
		renderBoard(tab.querySelector('.board'), board, ctx, refreshBoard);
		return board;
	};
	const board = await refreshBoard();
	renderMine(tab.querySelector('.mine'), board, ctx, refreshBoard);
	renderReminder(tab.querySelector('.reminder'), board, ctx);
	await mountManage(tab.querySelector('.manage'), ctx);
	return {
		events: EVENTS,
		refresh: async (message) => {
			await refreshBoard();
			// 관리 영역(게스트·호스트)은 모임 단위 이벤트나 재연결 때만 다시 그린다(입력 중인 폼 보호)
			if (!message || !EVENTS.has(message.type)) {
				await mountManage(tab.querySelector('.manage'), ctx);
			}
		},
	};
}

/**
 * 차수별 현황판. 체크인 버튼(나, 호스트는 다른 멤버)과 호스트의 실제 참석자 확정 버튼을 붙인다.
 * @param {HTMLElement} box 현황판 영역
 * @param {{responseDeadline: string|null, closed: boolean, rounds: Array}} board 현황판
 * @param {{me: {id: number}, detail: object}} ctx 모임 화면 상태
 * @param {() => Promise<unknown>} reload 다시 그리기
 */
function renderBoard(box, board, ctx, reload) {
	box.innerHTML = html`<div class="board-area">
		<p class="muted">⏰ ${board.closed ? '응답 마감됨' : `응답 마감 ${formatDate(board.responseDeadline)}`}</p>
		${raw(board.rounds.map((round) => roundBoard(round, ctx)).join(''))}
	</div>`;
	box.querySelector('.board-area').addEventListener('click', (event) => {
		const button = event.target.closest('button[data-act]');
		if (button) {
			runAction(event, async () => {
				await boardAction(button.dataset);
				await reload();
			});
		}
	});
}

/**
 * 차수 하나의 현황 카드: 참석자 목록(체크인 표시·버튼), 나머지 응답 칩, 호스트 확정 버튼.
 * @param {{roundId: number, name: string, attend: Array, absent: Array, undecided: Array, noResponse: Array,
 *   checkedInMemberIds: Array<number>, attendanceConfirmedAt: string|null}} round 차수 현황
 * @param {{me: {id: number}, detail: {hostAuthority: boolean}}} ctx 모임 화면 상태
 * @returns {string} HTML
 */
function roundBoard(round, ctx) {
	const checked = new Set(round.checkedInMemberIds);
	const rows = round.attend.map((member) => attendeeRow(member, round.roundId, checked.has(member.memberId), ctx));
	return html`<article>
		<div class="card-head"><h4>${round.name}</h4><span class="chip primary">참석 ${round.attend.length}명</span></div>
		${raw(round.attendanceConfirmedAt ? html`<p class="muted"><small>✅ 실제 참석자 확정 ${formatDate(round.attendanceConfirmedAt)}</small></p>` : '')}
		${raw(rows.length ? html`<ul class="list">${raw(rows.join(''))}</ul>` : html`<p class="empty">아직 참석하는 사람이 없어요</p>`)}
		${raw(peopleLine('불참', round.absent))}${raw(peopleLine('미정', round.undecided))}${raw(peopleLine('미응답', round.noResponse))}
		${raw(ctx.detail.hostAuthority ? html`<button type="button" class="secondary block-button" data-act="confirm" data-round="${round.roundId}">실제 참석자 확정</button>` : '')}
	</article>`;
}

/**
 * 참석자 한 줄: 썸네일·이름·체크인 칩, 나(호스트는 누구나)면 체크인 버튼.
 * @param {{memberId: number, nickname: string, emoji: string|null}} member 참석자
 * @param {number} roundId 차수 id
 * @param {boolean} isChecked 체크인했는지
 * @param {{me: {id: number}, detail: {hostAuthority: boolean}}} ctx 모임 화면 상태
 * @returns {string} HTML
 */
function attendeeRow(member, roundId, isChecked, ctx) {
	const isMe = member.memberId === ctx.me.id;
	const button = isMe || ctx.detail.hostAuthority
		? html`<button type="button" class="small ${isChecked ? 'secondary' : ''}" data-act="${isChecked ? 'uncheck' : 'check'}" data-round="${roundId}" data-member="${member.memberId}" data-me="${isMe}">${isChecked ? '체크인 취소' : '체크인'}</button>`
		: '';
	return html`<li><div class="list-row">
		${raw(thumb(member.nickname, member.memberId, {emoji: member.emoji, small: true}))}
		<span class="row-body"><span class="row-title">${raw(memberName(member))}${isMe ? ' (나)' : ''}</span></span>
		${raw(isChecked ? html`<span class="chip success">체크인</span>` : '')}${raw(button)}
	</div></li>`;
}

/**
 * "불참: 칩 칩" 꼴 한 줄. 비었으면 그리지 않는다.
 * @param {string} label 라벨
 * @param {Array} members 멤버
 * @returns {string} HTML
 */
function peopleLine(label, members) {
	return members.length ? html`<div class="people-line"><small>${label}</small><div>${raw(peopleChips(members))}</div></div>` : '';
}

/**
 * 현황판 버튼 하나를 처리한다.
 * @param {{act: string, round: string, member: string, me: string}} data 버튼 data 속성
 * @returns {Promise<void>}
 */
async function boardAction({act, round, member, me}) {
	const target = me === 'true' ? 'me' : member;
	if (act === 'check' || act === 'uncheck') {
		await api[act === 'check' ? 'put' : 'delete'](`/api/rounds/${round}/check-ins/${target}`);
		return;
	}
	if (act === 'confirm' && window.confirm('체크인한 사람으로 실제 참석자를 확정할까요? 참석이라 했는데 체크인 안 한 사람은 노쇼로 남아요.')) {
		const result = await api.post(`/api/rounds/${round}/attendance/confirm`);
		toast(`확정했어요 (바뀐 사람 ${result.changedCount}명)`);
	}
}

/**
 * 내 응답 폼. 차수별 참석·불참·미정과 단축 버튼(1차만·2차부터).
 * @param {HTMLElement} box 내 응답 영역
 * @param {{rounds: Array}} board 현황판(내 지금 응답을 읽는다)
 * @param {{meetingId: string, me: {id: number}}} ctx 모임 화면 상태
 * @param {() => Promise<object>} reloadBoard 현황판 다시 그리기
 */
function renderMine(box, board, ctx, reloadBoard) {
	const rows = board.rounds.map((round) => html`<div class="field-row"><span>${round.name}</span><div class="segmented">
		${raw(Object.entries(STATUS_LABELS).map(([status, label]) => html`<label><input type="radio" name="r${round.roundId}" value="${status}" ${raw(myStatus(round, ctx.me.id) === status ? 'checked' : '')}><span>${label}</span></label>`).join(''))}
	</div></div>`);
	box.innerHTML = html`<form class="mine-form"><h3 class="band">내 응답</h3>${raw(rows.join(''))}
		${raw(board.rounds.length > 1 ? html`<div class="button-row"><button type="button" class="secondary" data-shortcut="FIRST_ONLY">1차만</button>
			<button type="button" class="secondary" data-shortcut="LATE_FROM_SECOND">2차부터</button></div>` : '')}
		<button type="submit" class="block-button">응답 보내기</button>
	</form>`;
	const form = box.querySelector('.mine-form');
	const send = async (body) => {
		await api.put(`/api/meetings/${ctx.meetingId}/attendances/me`, body);
		toast('응답했어요');
		renderMine(box, await reloadBoard(), ctx, reloadBoard);
	};
	form.addEventListener('submit', (event) => runAction(event, () => send({rounds: board.rounds
		.map((round) => ({roundId: round.roundId, status: form.elements[`r${round.roundId}`].value}))
		.filter((item) => item.status)})));
	form.addEventListener('click', (event) => {
		const shortcut = event.target.closest('[data-shortcut]')?.dataset.shortcut;
		if (shortcut) {
			runAction(event, () => send({shortcut}));
		}
	});
}

/**
 * 현황판에서 내 지금 응답을 찾는다.
 * @param {{attend: Array, absent: Array, undecided: Array}} round 차수 현황
 * @param {number} myId 내 회원 id
 * @returns {string|null} ATTEND·ABSENT·UNDECIDED 또는 null(미응답)
 */
function myStatus(round, myId) {
	const has = (members) => members.some((member) => member.memberId === myId);
	if (has(round.attend)) {
		return 'ATTEND';
	}
	if (has(round.absent)) {
		return 'ABSENT';
	}
	return has(round.undecided) ? 'UNDECIDED' : null;
}

/**
 * 미응답자 리마인드: 카톡 문구 복사, 푸시 보내기(1시간 쿨다운). 마감됐으면 숨긴다.
 * @param {HTMLElement} box 리마인드 영역
 * @param {{closed: boolean}} board 현황판
 * @param {{meetingId: string}} ctx 모임 화면 상태
 */
function renderReminder(box, board, ctx) {
	if (board.closed) {
		box.innerHTML = '';
		return;
	}
	const base = `/api/meetings/${ctx.meetingId}/attendance-reminder`;
	box.innerHTML = html`<div class="reminder-area"><h3 class="band">미응답자 리마인드</h3>
		<div class="button-row"><button type="button" class="secondary" data-remind="copy">💬 카톡 문구 복사</button>
		<button type="button" class="secondary" data-remind="push">🔔 알림 보내기</button></div></div>`;
	box.querySelector('.reminder-area').addEventListener('click', (event) => {
		const kind = event.target.closest('[data-remind]')?.dataset.remind;
		if (kind) {
			runAction(event, () => (kind === 'copy' ? copyReminder(base) : pushReminder(base)));
		}
	});
}

/**
 * 리마인드 카톡 문구를 받아 복사한다.
 * @param {string} base 리마인드 API 경로
 * @returns {Promise<void>}
 */
async function copyReminder(base) {
	await copyText((await api.get(base)).shareText);
}

/**
 * 리마인드 푸시를 보낸다. 쿨다운(429)이면 다음 가능 시각을 알려 준다.
 * @param {string} base 리마인드 API 경로
 * @returns {Promise<void>}
 */
async function pushReminder(base) {
	try {
		const result = await api.post(`${base}/push`);
		toast(`알림을 보냈어요 (${result.noResponse.length}명)`);
	} catch (error) {
		if (error instanceof ApiError && error.details?.nextAvailableAt) {
			throw new Error(`${error.message} 다음 가능: ${formatDate(error.details.nextAvailableAt)}`);
		}
		throw error;
	}
}
