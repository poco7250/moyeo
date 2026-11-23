/* SPA 진입점: 화면 경로를 등록하고 상단 로그인 상태를 그린다 */

import {notFound, route, startRouter} from './router.js';
import {getMe, logout} from './session.js';
import {html, showError} from './ui.js';
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

/**
 * 상단 메뉴에 로그인 상태(닉네임·설정·로그아웃 또는 로그인 링크)를 그린다.
 * @returns {Promise<void>}
 */
async function renderSessionNav() {
	const nav = document.getElementById('nav-session');
	const me = await getMe();
	if (!me) {
		nav.innerHTML = html`<li><a href="/login" data-link>로그인</a></li>`;
		return;
	}
	nav.innerHTML = html`<li>${me.nickname}</li><li><a href="/settings" data-link>설정</a></li>
		<li><a href="#" id="logout">로그아웃</a></li>`;
	document.getElementById('logout').addEventListener('click', onLogout);
}

/**
 * 로그아웃하고 홈으로 새로 들어간다.
 * @param {MouseEvent} event 클릭 이벤트
 * @returns {Promise<void>}
 */
async function onLogout(event) {
	event.preventDefault();
	try {
		await logout();
		window.location.assign('/');
	} catch (error) {
		showError(error);
	}
}

renderSessionNav().catch(showError);
startRouter();
