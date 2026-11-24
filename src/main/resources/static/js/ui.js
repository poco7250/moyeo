/* 화면 공통 도우미: HTML 이스케이프, 토스트, 렌더링 */

const TOAST_MILLIS = 3000;

/**
 * HTML 특수문자를 이스케이프한다. 서버 값은 반드시 이걸 거쳐 innerHTML에 넣는다.
 * @param {unknown} value 표시할 값
 * @returns {string} 이스케이프된 문자열
 */
export function escapeHtml(value) {
	return String(value ?? '')
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
		.replaceAll("'", '&#39;');
}

/**
 * 템플릿 리터럴의 끼워 넣은 값을 자동으로 이스케이프한다. raw()로 감싼 값은 그대로 둔다.
 * @param {TemplateStringsArray} strings 고정 문자열
 * @param {...unknown} values 끼워 넣을 값
 * @returns {string} 완성된 HTML
 */
export function html(strings, ...values) {
	return strings.reduce((out, str, i) => {
		if (i >= values.length) {
			return out + str;
		}
		const value = values[i];
		const text = value instanceof RawHtml ? value.value : escapeHtml(value);
		return out + str + text;
	}, '');
}

class RawHtml {
	constructor(value) {
		this.value = value;
	}
}

/**
 * 이미 안전한 HTML 조각(예: html``로 만든 것)을 이스케이프하지 않게 표시한다.
 * @param {string} value 안전한 HTML
 * @returns {RawHtml} 표시용 래퍼
 */
export function raw(value) {
	return new RawHtml(value);
}

/**
 * 메인 영역에 HTML을 그린다.
 * 돌려받은 #app 자체에는 이벤트 리스너를 달지 않는다(화면을 바꿔도 남아 쌓인다). 안쪽 요소에 단다.
 * @param {string} content html``로 만든 HTML
 * @returns {HTMLElement} 메인 영역
 */
export function render(content) {
	const app = document.getElementById('app');
	app.innerHTML = content;
	return app;
}

/**
 * 화면 아래에 잠깐 메시지를 띄운다.
 * @param {string} message 메시지
 * @param {'info'|'error'} kind 종류
 */
export function toast(message, kind = 'info') {
	const box = document.createElement('div');
	box.className = `toast ${kind}`;
	box.textContent = message;
	document.getElementById('toasts').append(box);
	setTimeout(() => box.remove(), TOAST_MILLIS);
}

/**
 * 에러를 토스트로 보여 준다. 서버 입력값 에러면 첫 필드 에러 문구를, 아니면 error.message를 쓴다.
 * @param {unknown} error 잡은 에러
 */
export function showError(error) {
	const fieldMessage = error?.fieldErrors?.[0]?.reason;
	const message = fieldMessage || (error instanceof Error && error.message) || '알 수 없는 오류가 났어요';
	toast(message, 'error');
}

// 선 아이콘(24px, currentColor). 아이콘 라이브러리를 들이지 않으려고 필요한 것만 둔다
const ICONS = {
	home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
	settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
	back: '<path d="m15 18-6-6 6-6"/>',
	chevron: '<path d="m9 18 6-6-6-6"/>',
	user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
};

/**
 * 선 아이콘 SVG를 돌려준다.
 * @param {keyof ICONS} name 아이콘 이름
 * @param {number} size 픽셀 크기
 * @returns {RawHtml} 표시용 래퍼
 */
export function icon(name, size = 24) {
	return raw(`<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor"
		stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name] ?? ''}</svg>`);
}
