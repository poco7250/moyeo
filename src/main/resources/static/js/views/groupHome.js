/* 그룹 홈(PRD 5장 1번): 다가오는 모임, 지난 모임, 명예의 전당 요약, 하위 메뉴 */

import {api} from '../api.js';
import {findGroup, formatDate, memberName, notMemberNotice} from '../common.js';
import {html, raw, render} from '../ui.js';

const SUMMARY_SIZE = 3;

/**
 * 그룹 홈을 그린다.
 * @param {{params: {groupId: string}}} ctx 라우터 컨텍스트
 * @returns {Promise<void>}
 */
export async function groupHomeView({params}) {
	const group = await findGroup(params.groupId);
	if (!group) {
		render(notMemberNotice());
		return;
	}
	const base = `/api/groups/${group.id}`;
	const [meetings, fame] = await Promise.all([api.get(`${base}/meetings`), api.get(`${base}/hall-of-fame`)]);
	render(html`
		<h2>${group.name}</h2>
		${raw(menu(group.id))}
		<a href="/groups/${group.id}/meetings/new" data-link role="button">모임 만들기</a>
		<h3>다가오는 모임</h3>
		${raw(meetingList(meetings.upcoming, '예정된 모임이 없어요'))}
		<h3>지난 모임</h3>
		${raw(meetingList(meetings.past.slice(0, SUMMARY_SIZE), '지난 모임이 없어요'))}
		<a href="/groups/${group.id}/history" data-link>지난 모임 전체 보기</a>
		<h3>명예의 전당</h3>
		${raw(penaltySummary(fame.penaltyRanking))}
	`);
}

/**
 * 그룹 하위 메뉴 링크.
 * @param {number} groupId 그룹 id
 * @returns {string} HTML
 */
function menu(groupId) {
	return html`
		<nav><ul>
			<li><a href="/groups/${groupId}/members" data-link>멤버·초대</a></li>
			<li><a href="/groups/${groupId}/me" data-link>내 정보</a></li>
			<li><a href="/groups/${groupId}/hall-of-fame" data-link>명예의 전당</a></li>
			<li><a href="/groups/${groupId}/year-review" data-link>연말 결산</a></li>
		</ul></nav>
	`;
}

/**
 * 모임 목록(모임 상세 화면 링크).
 * @param {Array<{id: number, title: string, startAt: string|null, dateStatus: string}>} meetings 모임
 * @param {string} emptyText 비었을 때 문구
 * @returns {string} HTML
 */
function meetingList(meetings, emptyText) {
	if (meetings.length === 0) {
		return html`<p class="muted">${emptyText}</p>`;
	}
	const items = meetings.map((meeting) => html`
		<li><a href="/meetings/${meeting.id}" data-link>${meeting.title}</a>
			<small class="muted">${meeting.dateStatus === 'VOTING' ? '날짜 투표 중' : formatDate(meeting.startAt)}</small></li>
	`);
	return html`<ul>${raw(items.join(''))}</ul>`;
}

/**
 * 벌칙 순위 상위 몇 명.
 * @param {Array<{rank: number, member: object, penaltyCount: number}>} ranking 벌칙 순위
 * @returns {string} HTML
 */
function penaltySummary(ranking) {
	const top = ranking.filter((row) => row.penaltyCount > 0).slice(0, SUMMARY_SIZE);
	if (top.length === 0) {
		return html`<p class="muted">아직 벌칙 기록이 없어요</p>`;
	}
	const items = top.map((row) => html`<li>${row.rank}위 ${raw(memberName(row.member))} — 벌칙 ${row.penaltyCount}회</li>`);
	return html`<ol>${raw(items.join(''))}</ol>`;
}
