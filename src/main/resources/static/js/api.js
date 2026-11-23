/*
 * API 호출은 모두 이 모듈을 거친다(ROADMAP Phase 14 공통 규칙).
 * 서버 공통 응답 {success, data, error}를 풀어 data를 돌려주고, 실패면 ApiError를 던진다.
 * 401이면 refresh를 한 번 시도해 다시 부르고, 그래도 401이면 카카오 로그인으로 보낸다.
 */

const REFRESH_URL = '/api/auth/refresh';
const LOGIN_URL = '/api/auth/kakao/login';

/** 서버가 실패 응답을 줬을 때 던지는 에러 */
export class ApiError extends Error {
	/**
	 * @param {number} status HTTP 상태 코드
	 * @param {{code?: string, message?: string, fieldErrors?: Array, details?: unknown}} error 서버 에러 본문
	 */
	constructor(status, error) {
		super(error?.message || `요청이 실패했어요 (${status})`);
		this.name = 'ApiError';
		this.status = status;
		this.code = error?.code ?? null;
		this.fieldErrors = error?.fieldErrors ?? [];
		this.details = error?.details ?? null;
	}
}

let refreshing = null;

/**
 * 카카오 로그인 시작 URL을 만든다. 로그인이 끝나면 returnUrl로 돌아온다.
 * @param {string} returnUrl 돌아올 경로('/'로 시작하는 상대 경로)
 * @returns {string} 로그인 시작 URL
 */
export function loginUrl(returnUrl = currentPath()) {
	return `${LOGIN_URL}?returnUrl=${encodeURIComponent(returnUrl)}`;
}

/**
 * 지금 화면 경로(쿼리 포함)를 돌려준다.
 * @returns {string} 현재 경로
 */
export function currentPath() {
	return window.location.pathname + window.location.search;
}

/**
 * 토큰을 재발급한다. 동시에 여러 요청이 401을 받아도 refresh는 한 번만 보낸다.
 * @returns {Promise<boolean>} 재발급 성공 여부
 */
export function refreshSession() {
	if (!refreshing) {
		refreshing = fetch(REFRESH_URL, {method: 'POST', credentials: 'same-origin'})
			.then((response) => response.ok)
			.catch(() => false)
			.finally(() => {
				refreshing = null;
			});
	}
	return refreshing;
}

/**
 * API를 부르고 응답의 data를 돌려준다.
 * @param {string} method HTTP 메서드
 * @param {string} path '/api/...' 경로
 * @param {{body?: unknown, redirectOnAuth?: boolean}} options body는 객체(JSON) 또는 FormData,
 *   redirectOnAuth=false면 로그인 화면으로 보내지 않고 401 ApiError를 던진다
 * @returns {Promise<any>} 응답 data
 */
export async function request(method, path, {body, redirectOnAuth = true} = {}) {
	let response = await send(method, path, body);
	if (response.status === 401 && await refreshSession()) {
		response = await send(method, path, body);
	}
	if (response.status === 401 && redirectOnAuth) {
		window.location.assign(loginUrl());
	}
	return unwrap(response);
}

/**
 * fetch 한 번을 보낸다. 쿠키 인증이라 same-origin 자격 증명을 싣는다.
 * @param {string} method HTTP 메서드
 * @param {string} path 경로
 * @param {unknown} body 요청 본문
 * @returns {Promise<Response>} 응답
 */
function send(method, path, body) {
	const init = {method, credentials: 'same-origin', headers: {Accept: 'application/json'}};
	if (body instanceof FormData) {
		init.body = body;
	} else if (body !== undefined) {
		init.headers['Content-Type'] = 'application/json';
		init.body = JSON.stringify(body);
	}
	return fetch(path, init);
}

/**
 * 공통 응답을 풀어 data를 돌려주거나 ApiError를 던진다.
 * @param {Response} response 응답
 * @returns {Promise<any>} data
 */
async function unwrap(response) {
	const payload = await response.json().catch(() => null);
	if (response.ok && payload?.success !== false) {
		return payload?.data ?? null;
	}
	throw new ApiError(response.status, payload?.error);
}

/** 자주 쓰는 메서드 줄임 */
export const api = {
	get: (path, options) => request('GET', path, options),
	post: (path, body, options) => request('POST', path, {...options, body}),
	put: (path, body, options) => request('PUT', path, {...options, body}),
	patch: (path, body, options) => request('PATCH', path, {...options, body}),
	delete: (path, options) => request('DELETE', path, options),
};
