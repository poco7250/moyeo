/* 홈 화면: 비로그인이면 소개, 로그인이면 인사 카드·내 그룹 목록·그룹 만들기 */

import {api} from '../api.js';
import {absoluteUrl, copyText, formValues, thumb} from '../common.js';
import {navigate} from '../router.js';
import {getMe} from '../session.js';
import {html, icon, raw, render, showError} from '../ui.js';

const dayFormat = new Intl.DateTimeFormat('ko-KR', {year: 'numeric', month: 'long', day: 'numeric'});

/**
 * 로그인 여부에 따라 소개 또는 그룹 목록을 그린다.
 * @returns {Promise<void>}
 */
export async function homeView() {
	const me = await getMe();
	if (!me) {
		render(landing());
		return;
	}
	const groups = await api.get('/api/groups');
	const app = render(html`
		<div class="hero">
			<h2>${me.nickname}님, 안녕하세요 👋</h2>
			<p>오늘은 누구랑 모여 볼까요?</p>
		</div>
		<div class="section-head"><h3>내 그룹 <small>${groups.length}</small></h3></div>
		${raw(groupList(groups))}
		<h3>새 그룹 만들기</h3>
		<form id="group-create">
			<fieldset role="group">
				<input name="name" placeholder="그룹 이름 (예: 고등학교 친구들)" maxlength="50" required>
				<button type="submit">만들기</button>
			</fieldset>
		</form>
		<div id="created"></div>
	`);
	app.querySelector('#group-create').addEventListener('submit', onCreate);
}

/**
 * 비로그인 소개 화면.
 * @returns {string} HTML
 */
function landing() {
	return html`
		<div class="hero">
			<h2>친구 모임, 링크 하나로 👋</h2>
			<p>모임 공지·장소 투표·참석 체크·벌칙 게임·정산까지 단톡방 대신 여기서.</p>
		</div>
		<ul class="list">
			${raw(feature('📍', '장소 정하기', '후보 올리고 투표로 바로 확정'))}
			${raw(feature('🙋', '참석 체크', '누가 오는지 한눈에, 미응답자 리마인드'))}
			${raw(feature('🎲', '벌칙 게임', '사다리·꽝뽑기·룰렛으로 공정하게'))}
			${raw(feature('💸', '정산', '벌칙까지 반영한 1/N, 송금 안내까지'))}
		</ul>
		<a href="/login" data-link role="button" class="block-button">시작하기</a>
	`;
}

/**
 * 소개 목록 한 줄.
 * @param {string} emoji 아이콘
 * @param {string} title 제목
 * @param {string} sub 설명
 * @returns {string} HTML
 */
function feature(emoji, title, sub) {
	return html`<li><div class="list-row">
		<span class="icon-tile">${emoji}</span>
		<span class="row-body"><span class="row-title">${title}</span><span class="row-sub">${sub}</span></span>
	</div></li>`;
}

/**
 * 그룹 목록을 그린다.
 * @param {Array<{id: number, name: string, createdAt: string}>} groups 내 그룹
 * @returns {string} HTML
 */
function groupList(groups) {
	if (groups.length === 0) {
		return html`<p class="empty">아직 그룹이 없어요.<br>새로 만들거나 초대 링크로 참여해 보세요.</p>`;
	}
	const items = groups.map((group) => html`
		<li><a href="/groups/${group.id}" data-link class="list-row">
			${raw(thumb(group.name, group.id))}
			<span class="row-body">
				<span class="row-title">${group.name}</span>
				<span class="row-sub">${dayFormat.format(new Date(group.createdAt))}부터 함께</span>
			</span>
			<span class="row-end">${icon('chevron', 20)}</span>
		</a></li>
	`);
	return html`<ul class="list">${raw(items.join(''))}</ul>`;
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
				<p><strong>${group.name}</strong> 그룹을 만들었어요 🎉<br>초대 링크를 단톡방에 보내 보세요.</p>
				<p><code>${absoluteUrl(inviteLink.url)}</code></p>
				<div class="button-row">
					<button type="button" id="copy-invite">링크 복사</button>
					<button type="button" id="go-group" class="secondary">그룹으로 가기</button>
				</div>
			</article>
		`;
		box.querySelector('#copy-invite').addEventListener('click', () => copyText(absoluteUrl(inviteLink.url)));
		box.querySelector('#go-group').addEventListener('click', () => navigate(`/groups/${group.id}`));
	} catch (error) {
		showError(error);
	}
}
