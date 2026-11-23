/* 로그인한 회원 정보를 들고 있다. 화면마다 /api/members/me를 다시 부르지 않게 한 번만 받는다 */

import {ApiError, api} from './api.js';

let current;

/**
 * 로그인한 회원을 돌려준다. 로그인 전이면 null(로그인 화면으로 보내지 않는다).
 * @param {{force?: boolean}} options force=true면 서버에서 다시 받는다
 * @returns {Promise<{id: number, nickname: string, profileImageUrl: string|null}|null>} 회원 또는 null
 */
export async function getMe({force = false} = {}) {
	if (current !== undefined && !force) {
		return current;
	}
	try {
		current = await api.get('/api/members/me', {redirectOnAuth: false});
	} catch (error) {
		if (!(error instanceof ApiError) || error.status !== 401) {
			throw error;
		}
		current = null;
	}
	return current;
}

/**
 * 로그아웃하고 저장해 둔 회원 정보를 지운다.
 * @returns {Promise<void>}
 */
export async function logout() {
	await api.post('/api/auth/logout', undefined, {redirectOnAuth: false});
	current = null;
}
