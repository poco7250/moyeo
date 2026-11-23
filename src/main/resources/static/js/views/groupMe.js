/* 내 그룹 정보: 그룹 닉네임·이모지(G-2), 송금 정보(G-5) */

import {api} from '../api.js';
import {findGroup, formValues, groupHeader, notMemberNotice} from '../common.js';
import {getMe} from '../session.js';
import {html, raw, render, showError, toast} from '../ui.js';

/**
 * 내 그룹 프로필과 송금 정보 폼을 그린다.
 * @param {{params: {groupId: string}}} ctx 라우터 컨텍스트
 * @returns {Promise<void>}
 */
export async function groupMeView({params}) {
	const group = await findGroup(params.groupId);
	if (!group) {
		render(notMemberNotice());
		return;
	}
	const base = `/api/groups/${group.id}/members`;
	const [me, members, payment] = await Promise.all([getMe(), api.get(base), api.get(`${base}/me/payment`)]);
	const mine = members.find((member) => member.memberId === me.id);
	const app = render(html`
		${raw(groupHeader(group, '내 정보'))}
		${raw(profileForm(mine))}
		${raw(paymentForm(payment ?? {}))}
	`);
	app.querySelector('#profile').addEventListener('submit', (event) => submit(event, 'PATCH', `${base}/me`));
	app.querySelector('#payment').addEventListener('submit', (event) => submit(event, 'PUT', `${base}/me/payment`));
	app.querySelector('#payment-delete').addEventListener('click', () => removePayment(`${base}/me/payment`));
}

/**
 * 그룹 닉네임·이모지 폼.
 * @param {{nickname: string, emoji: string|null}} mine 내 그룹 멤버 정보
 * @returns {string} HTML
 */
function profileForm(mine) {
	return html`
		<form id="profile">
			<h3>그룹 프로필</h3>
			<label>닉네임 <input name="nickname" value="${mine.nickname}" maxlength="50" required></label>
			<label>이모지 <input name="emoji" value="${mine.emoji ?? ''}" maxlength="16" placeholder="😀"></label>
			<button type="submit">저장</button>
		</form>
	`;
}

/**
 * 송금 정보 폼. 정산 송금 안내에서 나에게 보낼 사람에게만 보인다.
 * @param {{bankName?: string, accountNumber?: string, accountHolder?: string, tossLink?: string, kakaoPayLink?: string}} payment 송금 정보
 * @returns {string} HTML
 */
function paymentForm(payment) {
	return html`
		<form id="payment">
			<h3>송금 정보</h3>
			<p class="muted">정산할 때 나에게 보낼 사람에게만 보여요.</p>
			<label>은행 <input name="bankName" value="${payment.bankName ?? ''}" maxlength="20"></label>
			<label>계좌번호 <input name="accountNumber" value="${payment.accountNumber ?? ''}" inputmode="numeric"></label>
			<label>예금주 <input name="accountHolder" value="${payment.accountHolder ?? ''}" maxlength="20"></label>
			<label>토스 송금 링크 <input name="tossLink" value="${payment.tossLink ?? ''}" type="url" placeholder="https://"></label>
			<label>카카오페이 송금 링크 <input name="kakaoPayLink" value="${payment.kakaoPayLink ?? ''}" type="url" placeholder="https://"></label>
			<button type="submit">저장</button>
			<button type="button" id="payment-delete" class="secondary">지우기</button>
		</form>
	`;
}

/**
 * 폼 값을 보내고 결과를 알려 준다.
 * @param {SubmitEvent} event 제출 이벤트
 * @param {string} method HTTP 메서드
 * @param {string} path API 경로
 * @returns {Promise<void>}
 */
async function submit(event, method, path) {
	event.preventDefault();
	try {
		await api[method.toLowerCase()](path, formValues(event.target));
		toast('저장했어요');
	} catch (error) {
		showError(error);
	}
}

/**
 * 송금 정보를 지우고 폼을 비운다.
 * @param {string} path API 경로
 * @returns {Promise<void>}
 */
async function removePayment(path) {
	if (!window.confirm('송금 정보를 지울까요?')) {
		return;
	}
	try {
		await api.delete(path);
		document.getElementById('payment').reset();
		document.querySelectorAll('#payment input').forEach((input) => {
			input.value = '';
		});
		toast('지웠어요');
	} catch (error) {
		showError(error);
	}
}
