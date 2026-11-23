/* 여러 화면이 같이 쓰는 표시·그룹 도우미 */

import {api} from './api.js';
import {html, raw, showError, toast} from './ui.js';

const ROLE_LABELS = {OWNER: '방장', TREASURER: '총무', MEMBER: '멤버'};
const dateFormat = new Intl.DateTimeFormat('ko-KR', {
	month: 'long', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit',
});
const wonFormat = new Intl.NumberFormat('ko-KR');
const YEARS_BACK = 5;

/**
 * 서버 LocalDateTime 문자열을 "11월 23일 (일) 오후 07:00" 꼴로 바꾼다.
 * @param {string|null} value 'yyyy-MM-ddTHH:mm:ss'
 * @returns {string} 표시 문자열(값이 없으면 '미정')
 */
export function formatDate(value) {
	return value ? dateFormat.format(new Date(value)) : '미정';
}

/**
 * 금액을 "12,000원" 꼴로 바꾼다.
 * @param {number} amount 원
 * @returns {string} 표시 문자열
 */
export function formatWon(amount) {
	return `${wonFormat.format(amount ?? 0)}원`;
}

/**
 * 그룹 역할 코드를 한글로 바꾼다.
 * @param {string} role OWNER·TREASURER·MEMBER
 * @returns {string} 방장·총무·멤버
 */
export function roleLabel(role) {
	return ROLE_LABELS[role] ?? role;
}

/**
 * 멤버를 "😀 닉네임" 꼴로 보여 준다(게스트면 표시를 붙인다).
 * @param {{nickname: string, emoji?: string|null, guest?: boolean}} member 멤버
 * @returns {string} 이스케이프된 HTML
 */
export function memberName(member) {
	return html`${member.emoji ? `${member.emoji} ` : ''}${member.nickname}${member.guest ? ' (게스트)' : ''}`;
}

/**
 * 글자를 클립보드에 복사한다. 막힌 환경(인앱 브라우저 등)이면 직접 복사하도록 띄운다.
 * @param {string} text 복사할 글자
 * @returns {Promise<void>}
 */
export async function copyText(text) {
	try {
		await navigator.clipboard.writeText(text);
		toast('복사했어요');
	} catch {
		window.prompt('아래 내용을 복사해 주세요', text);
	}
}

/**
 * 내 그룹 목록에서 그룹 하나를 찾는다(그룹 단건 조회 API가 없어서 목록을 쓴다).
 * @param {number|string} groupId 그룹 id
 * @returns {Promise<{id: number, name: string, createdAt: string}|null>} 그룹 또는 null(멤버가 아님)
 */
export async function findGroup(groupId) {
	const groups = await api.get('/api/groups');
	return groups.find((group) => String(group.id) === String(groupId)) ?? null;
}

/**
 * 그룹 하위 화면 위에 그룹 이름과 그룹 홈 링크를 그린다.
 * @param {{id: number, name: string}} group 그룹
 * @param {string} title 화면 제목
 * @returns {string} HTML
 */
export function groupHeader(group, title) {
	return html`
		<p><a href="/groups/${group.id}" data-link>← ${group.name}</a></p>
		<h2>${title}</h2>
	`;
}

/**
 * 그룹 멤버가 아닐 때 보여 줄 안내.
 * @returns {string} HTML
 */
export function notMemberNotice() {
	return html`
		<h2>그룹을 볼 수 없어요</h2>
		<p>이 그룹의 멤버가 아니에요. 초대 링크로 먼저 참여해 주세요.</p>
		<a href="/" data-link>홈으로</a>
	`;
}

/**
 * 폼 입력값을 객체로 모은다. 빈 문자열은 그대로 둔다(서버가 정규화한다).
 * @param {HTMLFormElement} form 폼
 * @returns {Object<string, string>} 이름 → 값
 */
export function formValues(form) {
	return Object.fromEntries(new FormData(form).entries());
}

/**
 * 상대 경로 링크를 지금 주소 기준 절대 URL로 바꾼다(카톡에 붙여 넣을 수 있게).
 * @param {string} url 서버가 준 링크
 * @returns {string} 절대 URL
 */
export function absoluteUrl(url) {
	return new URL(url, window.location.origin).href;
}

/**
 * 순위 표 하나.
 * @param {string} title 제목
 * @param {Array<{rank: number, member: object}>} rows 순위
 * @param {(row: object) => string} describe 오른쪽 설명
 * @returns {string} HTML
 */
export function rankSection(title, rows, describe) {
	const body = rows.length === 0
		? html`<p class="muted">기록이 없어요</p>`
		: html`<table><tbody>${raw(rows.map((row) => html`
			<tr><td>${row.rank}위</td><td>${raw(memberName(row.member))}</td><td>${describe(row)}</td></tr>
		`).join(''))}</tbody></table>`;
	return html`<h3>${title}</h3>${raw(body)}`;
}

/**
 * 올해부터 몇 년 전까지의 연도 option.
 * @param {string|null} selected 고른 연도
 * @returns {string} HTML
 */
export function yearOptions(selected) {
	const thisYear = new Date().getFullYear();
	const years = Array.from({length: YEARS_BACK}, (_, i) => String(thisYear - i));
	return years.map((year) => html`<option value="${year}" ${raw(year === selected ? 'selected' : '')}>${year}년</option>`)
		.join('');
}

/**
 * 서버 LocalDateTime 문자열을 datetime-local 입력값('yyyy-MM-ddTHH:mm')으로 바꾼다.
 * @param {string|null} value 서버 값
 * @returns {string} 입력값(없으면 빈 문자열)
 */
export function toLocalInput(value) {
	return value ? value.slice(0, 16) : '';
}

/**
 * datetime-local 입력값을 서버 LocalDateTime 문자열로 바꾼다.
 * @param {string} value 입력값
 * @returns {string|null} 'yyyy-MM-ddTHH:mm:00'(비었으면 null)
 */
export function fromLocalInput(value) {
	return value ? `${value}:00` : null;
}

/**
 * 기본 동작(폼 제출 등)을 막고 작업을 돌린다. 실패하면 토스트로 보여 준다.
 * @param {Event} event 이벤트
 * @param {() => Promise<unknown>} task 작업
 * @returns {Promise<void>}
 */
export async function runAction(event, task) {
	event.preventDefault();
	try {
		await task();
	} catch (error) {
		showError(error);
	}
}

/**
 * 모임 화면에서 쓸 회원 이름표를 만든다(그룹 멤버 + 이 모임 게스트). 정산·벌칙 응답은 id만 줘서 이걸로 이름을 찾는다.
 * @param {{groupId: number, guests: Array<{guestId: number, name: string}>}} detail 모임 상세
 * @returns {Promise<Map<number, {nickname: string, emoji: string|null, guest: boolean}>>} 회원 id → 표시 정보
 */
export async function memberDirectory(detail) {
	const members = await api.get(`/api/groups/${detail.groupId}/members`);
	const directory = new Map(members.map((member) => [member.memberId, {...member, guest: false}]));
	detail.guests.forEach((guest) => directory.set(guest.guestId, {nickname: guest.name, emoji: null, guest: true}));
	return directory;
}

/**
 * 이름표에서 회원 이름을 찾아 표시한다(없으면 '알 수 없음').
 * @param {Map<number, object>} directory memberDirectory 결과
 * @param {number} memberId 회원 id
 * @returns {string} 이스케이프된 HTML
 */
export function nameOf(directory, memberId) {
	const member = directory.get(memberId);
	return member ? memberName(member) : html`알 수 없음(#${memberId})`;
}

/**
 * https 링크만 새 창 링크로 그린다(사용자가 넣은 링크·서버가 만든 지도 링크용). 아니면 빈 문자열.
 * @param {string|null} url 링크
 * @param {string} label 링크 글자
 * @returns {string} HTML
 */
export function safeLink(url, label) {
	return url && url.startsWith('https://')
		? html`<a href="${url}" target="_blank" rel="noopener noreferrer">${label}</a>` : '';
}
