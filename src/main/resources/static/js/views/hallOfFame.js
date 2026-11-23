/* 명예의 전당(H-2, M-8): 벌칙·보드게임 꼴등·노쇼 순위, 출석부. 기간 = 전체 또는 연도 */

import {api} from '../api.js';
import {findGroup, formatDate, formatWon, groupHeader, memberName, notMemberNotice, rankSection, yearOptions}
	from '../common.js';
import {navigate} from '../router.js';
import {html, raw, render} from '../ui.js';

/**
 * 명예의 전당을 그린다. 연도는 ?year= 쿼리로 받는다(없으면 전체).
 * @param {{params: {groupId: string}, query: URLSearchParams}} ctx 라우터 컨텍스트
 * @returns {Promise<void>}
 */
export async function hallOfFameView({params, query}) {
	const group = await findGroup(params.groupId);
	if (!group) {
		render(notMemberNotice());
		return;
	}
	const year = query.get('year');
	const fame = await api.get(`/api/groups/${group.id}/hall-of-fame${year ? `?year=${encodeURIComponent(year)}` : ''}`);
	const app = render(html`
		${raw(groupHeader(group, '명예의 전당'))}
		<select id="fame-year">
			<option value="">전체 기간</option>${raw(yearOptions(year))}
		</select>
		${raw(rankSection('벌칙 순위', fame.penaltyRanking,
		(row) => `벌칙 ${row.penaltyCount}회 · ${formatWon(row.penaltyAmount)}`))}
		${raw(rankSection('보드게임 꼴등', fame.boardGameLoserRanking,
		(row) => `꼴등 ${row.loserCount}회 / ${row.playCount}판`))}
		${raw(rankSection('노쇼', fame.noShowRanking, (row) => `노쇼 ${row.noShowCount}회`))}
		${raw(attendanceLog(fame.attendanceLog))}
	`);
	app.querySelector('#fame-year').addEventListener('change', (event) => {
		const value = event.target.value;
		navigate(`/groups/${group.id}/hall-of-fame${value ? `?year=${value}` : ''}`, {replace: true});
	});
}

/**
 * 날짜별 출석부.
 * @param {Array<{meetingId: number, title: string, startAt: string, attendees: Array, noShows: Array}>} log 출석부
 * @returns {string} HTML
 */
function attendanceLog(log) {
	if (log.length === 0) {
		return html`<h3>출석부</h3><p class="muted">기록이 없어요</p>`;
	}
	const items = log.map((row) => html`
		<li><strong>${formatDate(row.startAt)}</strong> ${row.title}
			— ${raw(row.attendees.map(memberName).join(', ') || '참석자 없음')}
			${raw(row.noShows.length ? html`<small class="muted">(노쇼: ${raw(row.noShows.map(memberName).join(', '))})</small>` : '')}</li>
	`);
	return html`<h3>출석부</h3><ul>${raw(items.join(''))}</ul>`;
}
