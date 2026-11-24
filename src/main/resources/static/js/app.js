/* SPA 진입점: 화면 경로를 등록하고 상단 바 로그인 상태와 하단 탭을 그린다 */

import {thumb} from './common.js';
import {notFound, route, startRouter} from './router.js';
import {getMe} from './session.js';
import {html, icon, raw, showError} from './ui.js';
import {groupHistoryView} from './views/groupHistory.js';
import {groupHomeView} from './views/groupHome.js';
import {groupMeView} from './views/groupMe.js';
import {groupMembersView} from './views/groupMembers.js';
import {hallOfFameView} from './views/hallOfFame.js';
import {gamePlayView} from './views/gamePlay.js';
import {homeView} from './views/home.js';
import {inviteView} from './views/invite.js';
import {loginView} from './views/login.js';
import {meetingView} from './views/meeting.js';
import {meetingCreateView} from './views/meetingCreate.js';
import {notFoundView} from './views/notFound.js';
import {settingsView} from './views/settings.js';
import {yearReviewView} from './views/yearReview.js';

// 여기 경로는 서버 SpaRoutes.SCREEN_PATHS 안에 있어야 새로고침이 된다
route('/', homeView);
route('/login', loginView);
route('/settings', settingsView);
route('/invites/:token', inviteView);
route('/groups/:groupId', groupHomeView);
route('/groups/:groupId/members', groupMembersView);
route('/groups/:groupId/me', groupMeView);
route('/groups/:groupId/history', groupHistoryView);
route('/groups/:groupId/hall-of-fame', hallOfFameView);
route('/groups/:groupId/year-review', yearReviewView);
route('/groups/:groupId/meetings/new', meetingCreateView);
route('/meetings/:meetingId', meetingView);
route('/meetings/:meetingId/games/:gameId', gamePlayView);
notFound(notFoundView);

const TABS = [
	{href: '/', label: '홈', icon: 'home', match: (path) => path === '/' || path.startsWith('/groups') || path.startsWith('/meetings')},
	{href: '/settings', label: '설정', icon: 'settings', match: (path) => path.startsWith('/settings')},
];

/**
 * 상단 바 오른쪽(로그인이면 내 프로필 썸네일, 아니면 로그인 링크)과 하단 탭을 그린다.
 * @returns {Promise<void>}
 */
async function renderSessionNav() {
	const nav = document.getElementById('nav-session');
	const me = await getMe();
	if (!me) {
		nav.innerHTML = html`<a href="/login" data-link>로그인</a>`;
		return;
	}
	nav.innerHTML = html`<a href="/settings" data-link title="${me.nickname}">${raw(thumb(me.nickname, me.id, {image: me.profileImageUrl, small: true}))}</a>`;
	const tabbar = document.getElementById('tabbar');
	tabbar.hidden = false;
	renderTabbar();
	window.addEventListener('routechange', renderTabbar);
}

/**
 * 하단 탭을 그리고 지금 경로에 맞는 탭을 표시한다.
 */
function renderTabbar() {
	const path = window.location.pathname;
	document.getElementById('tabbar').innerHTML = TABS.map((tab) => html`
		<a href="${tab.href}" data-link ${raw(tab.match(path) ? 'aria-current="page"' : '')}>${icon(tab.icon)}${tab.label}</a>
	`).join('');
}

renderSessionNav().catch(showError);
startRouter();
