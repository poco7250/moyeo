/* 게임 탭과 게임 실행 화면이 같이 쓰는 표시·운영 도우미 */

import {api} from '../../api.js';

export const GAME_TYPES = {LADDER: '사다리', DRAW: '꽝뽑기', ROULETTE: '룰렛', ORDER: '순서 정하기'};
export const GAME_STATUS = {WAITING: '시작 전', PLAYING: '진행 중', REVEALED: '결과 공개', CLOSED: '종료', CANCELED: '취소'};
export const PENALTY_KINDS = {MONEY: '돈 벌칙', ACTION: '행동 벌칙', NONE: '벌칙 없음'};
export const PENALTY_STATUS = {ASSIGNED: '금액 조건 미설정', APPLIED: '금액 조건 적용', WAIVED: '금액 벌칙 없음', VOIDED: '무효(다시 돌림)'};

/**
 * 게임을 운영(시작·취소·종료·다시 돌리기)할 수 있는지: 진행자 또는 모임 호스트 권한(최종 판단은 서버 403).
 * @param {{createdBy: number}} game 게임 상태
 * @param {{me: {id: number}, detail: {hostAuthority: boolean}}} ctx 모임 화면 상태
 * @returns {boolean} 운영 가능 여부
 */
export function canOperate(game, ctx) {
	return game.createdBy === ctx.me.id || ctx.detail.hostAuthority;
}

/**
 * 지금 상태에서 보일 운영 버튼 종류.
 * @param {{status: string}} game 게임 상태
 * @returns {Array<'start'|'cancel'|'close'|'rerun'>} 버튼 목록
 */
export function availableActions(game) {
	const actions = {WAITING: ['start', 'cancel'], PLAYING: ['cancel'], REVEALED: ['close', 'rerun'], CLOSED: ['rerun']};
	return actions[game.status] ?? [];
}

export const ACTION_LABELS = {start: '시작', cancel: '취소', close: '게임 종료', rerun: '다시 돌리기'};

/**
 * 운영 요청을 보낸다. 취소·다시 돌리기는 확인창을 띄운다.
 * @param {number|string} gameId 게임 id
 * @param {'start'|'cancel'|'close'|'rerun'} action 운영 종류
 * @returns {Promise<object|null>} 바뀐 게임 상태(확인창에서 그만두면 null)
 */
export async function runGameAction(gameId, action) {
	const confirmText = {cancel: '게임을 취소할까요?', rerun: '다시 돌리면 새 회차가 생기고, 공개되면 이 회차 벌칙은 무효가 돼요. 할까요?'};
	if (confirmText[action] && !window.confirm(confirmText[action])) {
		return null;
	}
	return api.post(`/api/games/${gameId}/${action}`);
}
