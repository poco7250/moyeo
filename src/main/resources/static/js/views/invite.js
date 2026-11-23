/* 초대 진입 화면 자리. 미리보기·자동 가입은 Task 069에서 채운다 */

import {html, render} from '../ui.js';

/**
 * 초대 화면 자리표시를 그린다.
 * @param {{params: {token: string}}} ctx 라우터 컨텍스트
 */
export function inviteView({params}) {
	render(html`
		<h2>초대받았어요</h2>
		<p class="muted" data-invite-token="${params.token}">초대 미리보기는 곧 여기에 나와요.</p>
	`);
}
