/* 그룹 홈(PRD 5장 1번): 바로가기 메뉴, 다가오는 모임, 지난 모임, 명예의 전당 요약, 모임 만들기 버튼 */

import {api} from '../api.js';
import {backLink, findGroup, memberName, notMemberNotice, thumb} from '../common.js';
import {html, icon, raw, render} from '../ui.js';

const SUMMARY_SIZE = 3;
const MEDALS = ['🥇', '🥈', '🥉'];
const timeFormat = new Intl.DateTimeFormat('ko-KR', {weekday: 'short', hour: 'numeric', minute: '2-digit'});

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
		${raw(backLink('/', '내 그룹'))}
		<div class="list-row title-row">
			${raw(thumb(group.name, group.id))}
			<span class="row-body"><h2>${group.name}</h2></span>
		</div>
		${raw(menu(group.id))}
		<div class="section"><div class="section-head"><h3>다가오는 모임</h3></div></div>
		${raw(meetingList(meetings.upcoming, '예정된 모임이 없어요. 오른쪽 아래 + 로 만들어 보세요.', false))}
		<div class="section"><div class="section-head"><h3>지난 모임</h3>
			<a href="/groups/${group.id}/history" data-link>더보기</a></div></div>
		${raw(meetingList(meetings.past.slice(0, SUMMARY_SIZE), '지난 모임이 없어요', true))}
		<div class="section"><div class="section-head"><h3>명예의 전당</h3>
			<a href="/groups/${group.id}/hall-of-fame" data-link>더보기</a></div></div>
		${raw(penaltySummary(fame.penaltyRanking))}
		<a href="/groups/${group.id}/meetings/new" data-link role="button" class="fab" aria-label="모임 만들기">+</a>
	`);
}

/**
 * 그룹 바로가기 아이콘 메뉴.
 * @param {number} groupId 그룹 id
 * @returns {string} HTML
 */
function menu(groupId) {
	const items = [
		['members', '👥', '멤버·초대'],
		['me', '🙋', '내 정보'],
		['hall-of-fame', '🏆', '명예의 전당'],
		['year-review', '🗓️', '연말 결산'],
	];
	return html`<div class="menu-grid">${raw(items.map(([path, emoji, label]) => html`
		<a href="/groups/${groupId}/${path}" data-link><span class="menu-icon">${emoji}</span>${label}</a>
	`).join(''))}</div>`;
}

/**
 * 모임 목록(모임 상세 화면 링크). 왼쪽에 날짜 블록을 둔다.
 * @param {Array<{id: number, title: string, startAt: string|null, dateStatus: string}>} meetings 모임
 * @param {string} emptyText 비었을 때 문구
 * @param {boolean} past 지난 모임인지(날짜 블록을 흐리게)
 * @returns {string} HTML
 */
function meetingList(meetings, emptyText, past) {
	if (meetings.length === 0) {
		return html`<p class="empty">${emptyText}</p>`;
	}
	const items = meetings.map((meeting) => html`
		<li><a href="/meetings/${meeting.id}" data-link class="list-row">
			${raw(dateBlock(meeting, past))}
			<span class="row-body">
				<span class="row-title">${meeting.title}</span>
				<span class="row-sub">${raw(meetingSub(meeting))}</span>
			</span>
			<span class="row-end">${icon('chevron', 20)}</span>
		</a></li>
	`);
	return html`<ul class="list">${raw(items.join(''))}</ul>`;
}

/**
 * 모임 날짜 블록(월·일). 날짜가 없으면(투표 중) 물음표.
 * @param {{startAt: string|null}} meeting 모임
 * @param {boolean} past 지난 모임인지
 * @returns {string} HTML
 */
function dateBlock(meeting, past) {
	if (!meeting.startAt) {
		return html`<span class="date-block ${past ? 'past' : ''}"><b>?</b><small>미정</small></span>`;
	}
	const date = new Date(meeting.startAt);
	return html`<span class="date-block ${past ? 'past' : ''}">
		<small>${date.getMonth() + 1}월</small><b>${date.getDate()}</b></span>`;
}

/**
 * 모임 목록 둘째 줄(요일·시간 또는 날짜 투표 중 표시).
 * @param {{startAt: string|null, dateStatus: string}} meeting 모임
 * @returns {string} HTML
 */
function meetingSub(meeting) {
	if (meeting.dateStatus === 'VOTING') {
		return html`<span class="chip primary">날짜 투표 중</span>`;
	}
	return meeting.startAt ? html`${timeFormat.format(new Date(meeting.startAt))}` : html`날짜 미정`;
}

/**
 * 벌칙 순위 상위 몇 명.
 * @param {Array<{rank: number, member: object, penaltyCount: number}>} ranking 벌칙 순위
 * @returns {string} HTML
 */
function penaltySummary(ranking) {
	const top = ranking.filter((row) => row.penaltyCount > 0).slice(0, SUMMARY_SIZE);
	if (top.length === 0) {
		return html`<p class="empty">아직 벌칙 기록이 없어요</p>`;
	}
	const items = top.map((row) => html`
		<li><div class="list-row">
			<span class="icon-tile">${MEDALS[row.rank - 1] ?? row.rank}</span>
			<span class="row-body"><span class="row-title">${raw(memberName(row.member))}</span></span>
			<span class="chip danger">벌칙 ${row.penaltyCount}회</span>
		</div></li>
	`);
	return html`<ul class="list">${raw(items.join(''))}</ul>`;
}
