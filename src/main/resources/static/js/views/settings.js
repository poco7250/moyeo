/* 설정 화면: 내 프로필, 이 기기 웹 푸시 구독과 푸시 종류별 받기(Task 056 서버 API), 로그아웃 */

import {api} from '../api.js';
import {currentSubscription, pushSupported, serverPublicKey, subscribePush, unsubscribePush} from '../push.js';
import {thumb} from '../common.js';
import {getMe, logout} from '../session.js';
import {navigate} from '../router.js';
import {html, icon, raw, render, showError, toast} from '../ui.js';

const PUSH_LABELS = {
	ATTENDANCE_REMINDER: '참석 응답 리마인드',
	MEETING_CREATED: '새 모임',
	DATE_CONFIRMED: '모임 날짜 확정',
	PLACE_CONFIRMED: '장소 확정',
};

/**
 * 설정 화면을 그린다. 로그인이 필요하다.
 * @returns {Promise<void>}
 */
export async function settingsView() {
	const me = await getMe();
	if (!me) {
		navigate(`/login?returnUrl=${encodeURIComponent('/settings')}`, {replace: true});
		return;
	}
	const preferences = await api.get('/api/members/me/push-preferences');
	const root = render(html`<div id="settings-root">
		<h2>설정</h2>
		<div class="list-row profile-row">
			${raw(thumb(me.nickname, me.id, {image: me.profileImageUrl}))}
			<span class="row-body"><span class="row-title">${me.nickname}</span>
				<span class="row-sub">카카오 계정으로 로그인했어요</span></span>
		</div>
		<h3>이 기기 알림</h3>
		<div id="device-push"><p class="muted">확인 중…</p></div>
		<h3>받을 알림</h3>
		${raw(preferenceForm(preferences.enabled))}
		<h3>계정</h3>
		<ul class="menu-list"><li><button type="button" id="logout">로그아웃 ${icon('chevron', 20)}</button></li></ul>
	</div>`).querySelector('#settings-root');
	root.querySelector('#push-preferences').addEventListener('change', onPreferenceChange);
	root.querySelector('#logout').addEventListener('click', onLogout);
	await renderDevicePush(root.querySelector('#device-push'));
}

/**
 * 푸시 종류별 체크박스.
 * @param {Object<string, boolean>} enabled 종류 → 받기 여부
 * @returns {string} HTML
 */
function preferenceForm(enabled) {
	const rows = Object.entries(PUSH_LABELS).map(([type, label]) => html`
		<label class="switch-row">${label}<input type="checkbox" role="switch" name="${type}" ${raw(enabled[type] ? 'checked' : '')}></label>
	`);
	return html`<fieldset id="push-preferences">${raw(rows.join(''))}</fieldset>`;
}

/**
 * 체크박스 하나가 바뀌면 그 종류만 서버에 보낸다. 실패하면 되돌린다.
 * @param {Event} event change 이벤트
 * @returns {Promise<void>}
 */
async function onPreferenceChange(event) {
	const box = event.target;
	try {
		await api.patch('/api/members/me/push-preferences', {enabled: {[box.name]: box.checked}});
		toast('저장했어요');
	} catch (error) {
		box.checked = !box.checked;
		showError(error);
	}
}

/**
 * 이 기기 구독 상태와 켜기·끄기 버튼을 그린다.
 * @param {HTMLElement} box 그릴 곳
 * @returns {Promise<void>}
 */
async function renderDevicePush(box) {
	const publicKey = pushSupported() ? await serverPublicKey() : null;
	if (!pushSupported() || !publicKey) {
		box.innerHTML = html`<p class="muted">${pushSupported()
			? '서버에 푸시 설정이 없어 알림을 쓸 수 없어요.'
			: '이 브라우저는 웹 푸시를 지원하지 않아요. (아이폰은 홈 화면에 추가한 뒤 열어 주세요)'}</p>`;
		return;
	}
	const subscription = await currentSubscription();
	box.innerHTML = subscription
		? html`<p>이 기기에서 알림을 받고 있어요.</p><button type="button" class="secondary">알림 끄기</button>`
		: html`<p class="muted">이 기기는 알림을 받지 않아요.</p><button type="button">알림 켜기</button>`;
	box.querySelector('button').addEventListener('click', async () => {
		try {
			await (subscription ? unsubscribePush(subscription) : subscribePush(publicKey));
			await renderDevicePush(box);
		} catch (error) {
			showError(error);
		}
	});
}

/**
 * 로그아웃하고 홈으로 새로 들어간다(상단 바·하단 탭도 다시 그리게 새로고침).
 * @returns {Promise<void>}
 */
async function onLogout() {
	try {
		await logout();
		window.location.assign('/');
	} catch (error) {
		showError(error);
	}
}
