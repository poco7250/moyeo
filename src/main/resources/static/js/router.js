/*
 * History API 클라이언트 라우터. 서버는 화면 경로를 모두 index.html로 forward 한다(SpaForwardConfig).
 * 새 화면 경로를 만들면 서버 SpaRoutes.SCREEN_PATHS에도 같이 넣어야 새로고침이 된다.
 */

import {showError} from './ui.js';

const routes = [];
let notFoundView = null;
let cleanup = null;

/**
 * 화면 경로를 등록한다. ':이름' 부분은 params로 넘어간다.
 * 화면 함수가 함수를 돌려주면 다른 화면으로 갈 때 정리 함수로 부른다(구독 해제 등).
 * @param {string} pattern 예: '/invites/:token'
 * @param {(ctx: {params: Object<string,string>, query: URLSearchParams}) => (void|Function|Promise<void|Function>)} view 화면 함수
 */
export function route(pattern, view) {
	const names = [];
	const source = pattern.replace(/:(\w+)/g, (_, name) => {
		names.push(name);
		return '([^/]+)';
	});
	routes.push({regex: new RegExp(`^${source}/?$`), names, view});
}

/**
 * 맞는 경로가 없을 때 그릴 화면을 정한다.
 * @param {Function} view 화면 함수
 */
export function notFound(view) {
	notFoundView = view;
}

/**
 * 다른 화면으로 이동한다(history에 쌓는다).
 * @param {string} path 이동할 경로
 * @param {{replace?: boolean}} options replace=true면 history를 바꿔치기한다
 */
export function navigate(path, {replace = false} = {}) {
	if (replace) {
		window.history.replaceState(null, '', path);
	} else {
		window.history.pushState(null, '', path);
		window.scrollTo(0, 0);
	}
	renderCurrent();
}

/**
 * 라우터를 시작한다. data-link가 붙은 링크 클릭과 뒤로 가기를 가로챈다.
 */
export function startRouter() {
	document.addEventListener('click', (event) => {
		const link = event.target.closest('a[data-link]');
		if (!link || event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) {
			return;
		}
		event.preventDefault();
		navigate(link.getAttribute('href'));
	});
	window.addEventListener('popstate', renderCurrent);
	renderCurrent();
}

/**
 * 지금 주소에 맞는 화면을 그린다. 화면 함수가 던진 에러는 토스트로 보여 준다.
 * 그리기 전에 'routechange' 이벤트를 띄워 하단 탭 같은 공통 영역이 따라 바뀌게 한다.
 */
async function renderCurrent() {
	if (typeof cleanup === 'function') {
		cleanup();
	}
	cleanup = null;
	const {view, params} = match(window.location.pathname);
	window.dispatchEvent(new Event('routechange'));
	try {
		cleanup = await view({params, query: new URLSearchParams(window.location.search)});
	} catch (error) {
		showError(error);
	}
}

/**
 * 경로에 맞는 화면과 경로 변수를 찾는다.
 * @param {string} pathname 경로
 * @returns {{view: Function, params: Object<string,string>}} 화면과 경로 변수
 */
function match(pathname) {
	for (const {regex, names, view} of routes) {
		const found = pathname.match(regex);
		if (found) {
			const params = Object.fromEntries(names.map((name, i) => [name, decodeURIComponent(found[i + 1])]));
			return {view, params};
		}
	}
	return {view: notFoundView, params: {}};
}
