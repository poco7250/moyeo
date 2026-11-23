/* 연말 결산(H-4): 연도 선택, 요약·순위·많이 간 장소·벌칙왕·노쇼왕, 공유 문구 복사 */

import {api} from '../api.js';
import {copyText, findGroup, formatWon, groupHeader, memberName, notMemberNotice, rankSection, yearOptions}
	from '../common.js';
import {navigate} from '../router.js';
import {html, raw, render} from '../ui.js';

/**
 * 연말 결산을 그린다. 연도는 ?year= 쿼리, 없으면 올해.
 * @param {{params: {groupId: string}, query: URLSearchParams}} ctx 라우터 컨텍스트
 * @returns {Promise<void>}
 */
export async function yearReviewView({params, query}) {
	const group = await findGroup(params.groupId);
	if (!group) {
		render(notMemberNotice());
		return;
	}
	const year = query.get('year') ?? String(new Date().getFullYear());
	const review = await api.get(`/api/groups/${group.id}/year-review?year=${encodeURIComponent(year)}`);
	const app = render(html`
		${raw(groupHeader(group, `${year}년 결산`))}
		<select id="review-year">${raw(yearOptions(year))}</select>
		<p>모임 ${review.meetingCount}번 · 차수 ${review.roundCount}번 · 총 지출 ${formatWon(review.totalExpense)}
			<small class="muted">(정산 확정 ${review.confirmedMeetingCount}/${review.meetingCount}개 기준 부담액)</small></p>
		${raw(rankSection('참석 순위', review.attendanceRanking, (row) => `${row.attendCount}번`))}
		${raw(rankSection('부담액 순위', review.shareRanking, (row) => formatWon(row.shareAmount)))}
		${raw(placeList(review.topPlaces))}
		${raw(kings('벌칙왕', review.penaltyKings))}
		${raw(kings('노쇼왕', review.noShowKings))}
		${raw(review.shareText ? html`<button type="button" id="share-copy">카톡 공유 문구 복사</button>` : '')}
	`);
	app.querySelector('#review-year').addEventListener('change', (event) => {
		navigate(`/groups/${group.id}/year-review?year=${event.target.value}`, {replace: true});
	});
	app.querySelector('#share-copy')?.addEventListener('click', () => copyText(review.shareText));
}

/**
 * 많이 간 장소 목록.
 * @param {Array<{rank: number, label: string, visitCount: number}>} places 장소
 * @returns {string} HTML
 */
function placeList(places) {
	const body = places.length === 0
		? html`<p class="muted">기록이 없어요</p>`
		: html`<ol>${raw(places.map((place) => html`<li>${place.label} — ${place.visitCount}번</li>`).join(''))}</ol>`;
	return html`<h3>많이 간 장소</h3>${raw(body)}`;
}

/**
 * 공동 1위 목록(벌칙왕·노쇼왕).
 * @param {string} title 제목
 * @param {Array<{member: object}>} rows 1위들
 * @returns {string} HTML
 */
function kings(title, rows) {
	const names = rows.map((row) => memberName(row.member)).join(', ');
	return html`<h3>${title}</h3><p>${raw(names || '없음')}</p>`;
}
