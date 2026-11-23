/*
 * 모임 상세(PRD 5장 2번). 탭: 장소 / 참석 / 게임 / 정산.
 * 모임 토픽을 구독해 이벤트가 오면 지금 보이는 영역만 REST로 다시 받는다(payload는 해석하지 않음).
 */

import {ApiError, api} from '../api.js';
import {absoluteUrl, copyText, formatDate, notMemberNotice} from '../common.js';
import {subscribe} from '../realtime.js';
import {getMe} from '../session.js';
import {html, raw, render, showError} from '../ui.js';
import {mountAttendanceTab} from './meeting/attendance.js';
import {renderDateVote} from './meeting/date.js';
import {mountGameTab} from './meeting/game.js';
import {mountPlaceTab} from './meeting/place.js';
import {mountSettlementTab} from './meeting/settlement.js';

const TABS = {
	place: {label: '장소', mount: mountPlaceTab},
	attendance: {label: '참석', mount: mountAttendanceTab},
	game: {label: '게임', mount: mountGameTab},
	settlement: {label: '정산', mount: mountSettlementTab},
};
const MEETING_EVENTS = new Set(['DATE_VOTE_CHANGED', 'MEETING_DATE_CONFIRMED', 'MEETING_GUESTS_CHANGED',
	'MEETING_HOST_CHANGED']);

/**
 * 모임 상세를 그리고 실시간 구독을 건다. 돌려준 함수는 화면을 떠날 때 구독을 푼다.
 * @param {{params: {meetingId: string}, query: URLSearchParams}} ctx 라우터 컨텍스트
 * @returns {Promise<(() => void)|undefined>} 정리 함수
 */
export async function meetingView({params, query}) {
	const tab = TABS[query.get('tab')] ? query.get('tab') : 'place';
	const ctx = {meetingId: params.meetingId, me: await getMe(), detail: await loadDetail(params.meetingId)};
	if (!ctx.detail) {
		return undefined;
	}
	const root = render(html`<div id="meeting-root">
		<section id="meeting-info"></section>
		<section id="meeting-date"></section>
		${raw(tabNav(ctx.meetingId, tab))}
		<section id="tab-body"></section>
	</div>`).querySelector('#meeting-root');
	ctx.root = root;
	await renderMeetingLevel(ctx);
	const view = await TABS[tab].mount(root.querySelector('#tab-body'), ctx);
	const onMessage = (message) => handleMessage(message, ctx, view).catch(showError);
	return subscribe(`/topic/meetings/${ctx.meetingId}`, onMessage, {
		onResync: () => handleMessage(null, ctx, view).catch(showError),
	});
}

/**
 * 모임 상세를 받는다. 멤버가 아니면 안내를 그리고 null.
 * @param {string} meetingId 모임 id
 * @returns {Promise<object|null>} MeetingDetailResponse
 */
async function loadDetail(meetingId) {
	try {
		return await api.get(`/api/meetings/${encodeURIComponent(meetingId)}`);
	} catch (error) {
		if (error instanceof ApiError && [403, 404].includes(error.status)) {
			render(notMemberNotice());
			return null;
		}
		throw error;
	}
}

/**
 * 이벤트(재연결이면 null)에 맞는 영역을 다시 받는다.
 * @param {{type: string, data: any}|null} message 실시간 메시지
 * @param {object} ctx 모임 화면 상태
 * @param {{events: Set<string>, refresh: Function}} view 지금 탭
 * @returns {Promise<void>}
 */
async function handleMessage(message, ctx, view) {
	const meetingLevel = !message || MEETING_EVENTS.has(message.type);
	if (meetingLevel) {
		ctx.detail = await api.get(`/api/meetings/${ctx.meetingId}`);
		await renderMeetingLevel(ctx);
	}
	if (meetingLevel || view.events.has(message.type)) {
		await view.refresh(message);
	}
}

/**
 * 모임 정보와(날짜 투표 중이면) 날짜 투표 영역을 그린다.
 * @param {object} ctx 모임 화면 상태
 * @returns {Promise<void>}
 */
async function renderMeetingLevel(ctx) {
	const {detail} = ctx;
	const info = ctx.root.querySelector('#meeting-info');
	info.innerHTML = html`
		<p><a href="/groups/${detail.groupId}" data-link>← 그룹으로</a></p>
		<h2>${detail.title}</h2>
		<p>${detail.dateStatus === 'VOTING' ? '날짜 투표 중' : formatDate(detail.startAt)}
			${raw(detail.host ? html`<mark>내가 호스트</mark>` : '')}</p>
		${raw(detail.memo ? html`<p class="muted">${detail.memo}</p>` : '')}
		<button type="button" class="secondary" data-copy="share">공유 문구 복사</button>
		<button type="button" class="secondary outline" data-copy="url">링크 복사</button>
	`;
	info.querySelector('[data-copy="share"]').addEventListener('click', () => copyText(detail.shareText));
	info.querySelector('[data-copy="url"]').addEventListener('click', () => copyText(absoluteUrl(detail.url)));
	await renderDateVote(ctx.root.querySelector('#meeting-date'), ctx);
}

/**
 * 탭 메뉴. 탭은 ?tab= 쿼리로 바꾼다.
 * @param {string} meetingId 모임 id
 * @param {string} active 지금 탭
 * @returns {string} HTML
 */
function tabNav(meetingId, active) {
	const items = Object.entries(TABS).map(([key, {label}]) => html`
		<li><a href="/meetings/${meetingId}?tab=${key}" data-link ${raw(key === active ? 'aria-current="page"' : '')}>${label}</a></li>
	`);
	return html`<nav><ul>${raw(items.join(''))}</ul></nav>`;
}
