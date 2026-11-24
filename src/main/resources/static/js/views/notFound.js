/* 맞는 화면이 없을 때 */

import {html, render} from '../ui.js';

/**
 * 404 화면을 그린다.
 */
export function notFoundView() {
	render(html`
		<div class="empty">
			<p class="empty-icon">🧭</p>
			<h2>페이지를 찾을 수 없어요</h2>
			<a href="/" data-link role="button">홈으로</a>
		</div>
	`);
}
