/* 멤버·초대 화면: 멤버 목록·역할(G-2, G-4), 초대 링크 보기·복사·새로 만들기(G-1) */

import {api} from '../api.js';
import {absoluteUrl, copyText, findGroup, formatDate, groupHeader, notMemberNotice, roleLabel, thumb}
	from '../common.js';
import {getMe} from '../session.js';
import {html, raw, render, showError, toast} from '../ui.js';

/**
 * 멤버·초대 화면을 그린다. 방장에게만 역할 버튼을 보여 준다(최종 판단은 서버 403).
 * @param {{params: {groupId: string}}} ctx 라우터 컨텍스트
 * @returns {Promise<void>}
 */
export async function groupMembersView({params}) {
	const group = await findGroup(params.groupId);
	if (!group) {
		render(notMemberNotice());
		return;
	}
	const base = `/api/groups/${group.id}`;
	const [me, members, invite] = await Promise.all([
		getMe(), api.get(`${base}/members`), api.get(`${base}/invite-links/current`)]);
	const isOwner = members.some((member) => member.memberId === me.id && member.role === 'OWNER');
	// 리스너는 #app이 아니라 매번 새로 그려지는 안쪽 요소에 단다(다시 그릴 때 중복 방지)
	const root = render(html`<div id="members-root">
		${raw(groupHeader(group, '멤버·초대'))}
		${raw(inviteCard(invite))}
		<h3>멤버 ${members.length}명</h3>
		${raw(memberTable(members, me.id, isOwner))}
	</div>`).querySelector('#members-root');
	root.addEventListener('click', (event) => onClick(event, base, () => groupMembersView({params})));
}

/**
 * 초대 링크 카드. 없으면 만들기 버튼만 보여 준다.
 * @param {{url: string, expiresAt: string}|null} invite 현재 초대 링크
 * @returns {string} HTML
 */
function inviteCard(invite) {
	if (!invite) {
		return html`<article><p>쓸 수 있는 초대 링크가 없어요.</p>
			<button type="button" data-action="reissue">초대 링크 만들기</button></article>`;
	}
	return html`
		<article>
			<p><strong>초대 링크</strong> <small>만료 ${formatDate(invite.expiresAt)}</small></p>
			<p><code>${absoluteUrl(invite.url)}</code></p>
			<div class="button-row">
				<button type="button" data-action="copy" data-url="${absoluteUrl(invite.url)}">링크 복사</button>
				<button type="button" data-action="reissue" class="secondary">새로 만들기</button>
			</div>
		</article>
	`;
}

/**
 * 멤버 목록. 방장이면 다른 멤버 행에 총무 지정·해제와 방장 넘기기 버튼을 붙인다.
 * @param {Array<{memberId: number, nickname: string, emoji: string|null, role: string}>} members 멤버
 * @param {number} myId 내 회원 id
 * @param {boolean} isOwner 내가 방장인지
 * @returns {string} HTML
 */
function memberTable(members, myId, isOwner) {
	const rows = members.map((member) => html`
		<li><div class="list-row">
			${raw(thumb(member.nickname, member.memberId, {emoji: member.emoji, small: true}))}
			<span class="row-body">
				<span class="row-title">${member.nickname}${member.memberId === myId ? ' (나)' : ''}
					${raw(member.role === 'MEMBER' ? '' : html`<span class="chip primary">${roleLabel(member.role)}</span>`)}</span>
				${raw(isOwner && member.memberId !== myId ? html`<span class="row-actions">${raw(roleButtons(member))}</span>` : '')}
			</span>
		</div></li>
	`);
	return html`<ul class="list">${raw(rows.join(''))}</ul>`;
}

/**
 * 방장용 역할 버튼.
 * @param {{memberId: number, role: string}} member 대상 멤버
 * @returns {string} HTML
 */
function roleButtons(member) {
	const nextRole = member.role === 'TREASURER' ? 'MEMBER' : 'TREASURER';
	const label = member.role === 'TREASURER' ? '총무 해제' : '총무 지정';
	return html`
		<button type="button" class="secondary" data-action="role" data-id="${member.memberId}" data-role="${nextRole}">${label}</button>
		<button type="button" class="secondary" data-action="owner" data-id="${member.memberId}">방장 넘기기</button>
	`;
}

/**
 * 버튼 클릭을 처리하고, 바뀌었으면 화면을 다시 그린다.
 * @param {MouseEvent} event 클릭 이벤트
 * @param {string} base 그룹 API 경로
 * @param {() => Promise<void>} reload 다시 그리기
 * @returns {Promise<void>}
 */
async function onClick(event, base, reload) {
	const button = event.target.closest('button[data-action]');
	if (!button) {
		return;
	}
	try {
		const changed = await ACTIONS[button.dataset.action](button.dataset, base);
		if (changed) {
			await reload();
		}
	} catch (error) {
		showError(error);
	}
}

/** 버튼 종류별 처리. true를 돌려주면 화면을 다시 그린다 */
const ACTIONS = {
	copy: async ({url}) => {
		await copyText(url);
		return false;
	},
	reissue: async (_, base) => {
		if (!window.confirm('새 링크를 만들면 기존 초대 링크는 더 이상 쓸 수 없어요. 만들까요?')) {
			return false;
		}
		await api.post(`${base}/invite-links`);
		toast('새 초대 링크를 만들었어요');
		return true;
	},
	role: async ({id, role}, base) => {
		await api.put(`${base}/members/${id}/role`, {role});
		return true;
	},
	owner: async ({id}, base) => {
		if (!window.confirm('방장을 넘기면 나는 일반 멤버가 돼요. 넘길까요?')) {
			return false;
		}
		await api.post(`${base}/owner-transfer`, {memberId: Number(id)});
		return true;
	},
};
