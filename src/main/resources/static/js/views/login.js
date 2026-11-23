/* 로그인 화면. 카카오 로그인 후 returnUrl(없으면 홈)로 돌아온다 */

import {loginUrl} from '../api.js';
import {getMe} from '../session.js';
import {navigate} from '../router.js';
import {html, render} from '../ui.js';

/**
 * 이미 로그인했으면 돌아갈 곳으로 보내고, 아니면 카카오 로그인 버튼을 그린다.
 * @param {{query: URLSearchParams}} ctx 라우터 컨텍스트(returnUrl 쿼리)
 * @returns {Promise<void>}
 */
export async function loginView({query}) {
	const returnUrl = safeReturnUrl(query.get('returnUrl'));
	if (await getMe()) {
		navigate(returnUrl, {replace: true});
		return;
	}
	render(html`
		<h2>로그인</h2>
		<p>카카오 계정으로 바로 시작해요.</p>
		<a href="${loginUrl(returnUrl)}" role="button">카카오로 로그인</a>
	`);
}

/**
 * '/'로 시작하는 상대 경로만 받는다(서버 ReturnUrlValidator도 한 번 더 검사한다).
 * @param {string|null} value 쿼리 값
 * @returns {string} 돌아갈 경로
 */
function safeReturnUrl(value) {
	return value && value.startsWith('/') && !value.startsWith('//') ? value : '/';
}
