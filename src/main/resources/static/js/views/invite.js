/*
 * 초대 진입 화면(PRD 5장 4번, G-3). 비로그인이면 미리보기와 로그인 버튼,
 * 로그인 상태면 바로 가입해 그룹 홈으로 보낸다(이미 멤버여도 그룹 홈).
 */

import {ApiError, api, loginUrl} from '../api.js';
import {formatDate, thumb} from '../common.js';
import {navigate} from '../router.js';
import {getMe} from '../session.js';
import {html, raw, render} from '../ui.js';

/**
 * 초대 미리보기를 그리고, 로그인 상태면 가입까지 한다.
 * @param {{params: {token: string}}} ctx 라우터 컨텍스트
 * @returns {Promise<void>}
 */
export async function inviteView({params}) {
	const token = encodeURIComponent(params.token);
	const preview = await loadPreview(token);
	if (!preview) {
		return;
	}
	if (await getMe()) {
		const {groupId} = await api.post(`/api/invites/${token}/join`);
		navigate(`/groups/${groupId}`, {replace: true});
		return;
	}
	render(html`
		<div class="hero invite-hero">
			${raw(thumb(preview.groupName, preview.groupName))}
			<h2>${preview.groupName}</h2>
			<p>모임에 초대받았어요 💌</p>
		</div>
		<div class="stat-row">
			<div><b>${preview.memberCount}명</b><small>지금 멤버</small></div>
			<div><b>${formatDate(preview.expiresAt)}</b><small>링크 만료</small></div>
		</div>
		<a href="${loginUrl()}" role="button" class="kakao-button">카카오로 로그인하고 참여하기</a>
	`);
}

/**
 * 초대 미리보기를 받는다. 없거나 만료된 링크면 안내를 그리고 null을 돌려준다.
 * @param {string} token 인코딩한 초대 토큰
 * @returns {Promise<{groupName: string, memberCount: number, expiresAt: string}|null>} 미리보기
 */
async function loadPreview(token) {
	try {
		return await api.get(`/api/invites/${token}`, {redirectOnAuth: false});
	} catch (error) {
		if (!(error instanceof ApiError) || ![404, 410].includes(error.status)) {
			throw error;
		}
		render(html`
			<div class="empty">
				<h2>초대 링크를 쓸 수 없어요</h2>
				<p>${error.message}</p>
				<p class="muted">그룹 멤버에게 새 초대 링크를 받아 주세요.</p>
			</div>
		`);
		return null;
	}
}
