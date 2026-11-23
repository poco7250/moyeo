/* SPA 진입점: 화면 경로를 등록하고 상단 로그인 상태를 그린다 */

import {notFound, route, startRouter} from './router.js';
import {getMe, logout} from './session.js';
import {html, showError} from './ui.js';
import {homeView} from './views/home.js';
import {inviteView} from './views/invite.js';
import {loginView} from './views/login.js';
import {notFoundView} from './views/notFound.js';

// 여기 경로는 서버 SpaRoutes.SCREEN_PATHS 안에 있어야 새로고침이 된다
route('/', homeView);
route('/login', loginView);
route('/invites/:token', inviteView);
notFound(notFoundView);

/**
 * 상단 메뉴에 로그인 상태(닉네임·로그아웃 또는 로그인 링크)를 그린다.
 * @returns {Promise<void>}
 */
async function renderSessionNav() {
	const nav = document.getElementById('nav-session');
	const me = await getMe();
	if (!me) {
		nav.innerHTML = html`<li><a href="/login" data-link>로그인</a></li>`;
		return;
	}
	nav.innerHTML = html`<li>${me.nickname}</li><li><a href="#" id="logout">로그아웃</a></li>`;
	document.getElementById('logout').addEventListener('click', async (event) => {
		event.preventDefault();
		try {
			await logout();
			window.location.assign('/');
		} catch (error) {
			showError(error);
		}
	});
}

renderSessionNav().catch(showError);
startRouter();
