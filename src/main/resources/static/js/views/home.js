/* 홈 화면. 그룹 목록·그룹 홈은 Task 069에서 채운다 */

import {getMe} from '../session.js';
import {html, render} from '../ui.js';

/**
 * 로그인 여부에 따라 인사 또는 로그인 안내를 그린다.
 * @returns {Promise<void>}
 */
export async function homeView() {
	const me = await getMe();
	if (!me) {
		render(html`
			<h2>모여</h2>
			<p>모임 공지부터 정산까지 링크 하나로.</p>
			<a href="/login" data-link role="button">로그인</a>
		`);
		return;
	}
	render(html`
		<h2>${me.nickname}님, 안녕하세요</h2>
		<p class="muted">그룹 목록은 곧 여기에 나와요.</p>
	`);
}
