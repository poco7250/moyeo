/* 홈 화면: 비로그인이면 소개, 로그인이면 내 그룹 목록과 그룹 만들기 */

import {api} from '../api.js';
import {absoluteUrl, copyText, formValues} from '../common.js';
import {navigate} from '../router.js';
import {getMe} from '../session.js';
import {html, raw, render, showError} from '../ui.js';

/**
 * 로그인 여부에 따라 소개 또는 그룹 목록을 그린다.
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
	const groups = await api.get('/api/groups');
	const app = render(html`
		<h2>${me.nickname}님의 그룹</h2>
		${raw(groupList(groups))}
		<form id="group-create">
			<fieldset role="group">
				<input name="name" placeholder="새 그룹 이름" maxlength="50" required>
				<button type="submit">그룹 만들기</button>
			</fieldset>
		</form>
		<div id="created"></div>
	`);
	app.querySelector('#group-create').addEventListener('submit', onCreate);
}

/**
 * 그룹 목록을 그린다.
 * @param {Array<{id: number, name: string}>} groups 내 그룹
 * @returns {string} HTML
 */
function groupList(groups) {
	if (groups.length === 0) {
		return html`<p class="muted">아직 그룹이 없어요. 새로 만들거나 초대 링크로 참여해 보세요.</p>`;
	}
	const items = groups.map((group) => html`<li><a href="/groups/${group.id}" data-link>${group.name}</a></li>`);
	return html`<ul>${raw(items.join(''))}</ul>`;
}

/**
 * 그룹을 만들고 초대 링크를 보여 준다. 그룹 홈으로 가는 버튼도 같이 준다.
 * @param {SubmitEvent} event 제출 이벤트
 * @returns {Promise<void>}
 */
async function onCreate(event) {
	event.preventDefault();
	try {
		const {group, inviteLink} = await api.post('/api/groups', formValues(event.target));
		const box = document.getElementById('created');
		box.innerHTML = html`
			<article>
				<p><strong>${group.name}</strong> 그룹을 만들었어요. 초대 링크를 단톡방에 보내 보세요.</p>
				<p><code>${absoluteUrl(inviteLink.url)}</code></p>
				<button type="button" id="copy-invite">링크 복사</button>
				<button type="button" id="go-group" class="secondary">그룹으로 가기</button>
			</article>
		`;
		box.querySelector('#copy-invite').addEventListener('click', () => copyText(absoluteUrl(inviteLink.url)));
		box.querySelector('#go-group').addEventListener('click', () => navigate(`/groups/${group.id}`));
	} catch (error) {
		showError(error);
	}
}
