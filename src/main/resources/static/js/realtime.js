/*
 * STOMP 실시간 연결은 모두 이 모듈을 거친다(ROADMAP Phase 14 공통 규칙).
 * 구독이 하나라도 있으면 /ws에 연결하고, 끊기면 자동으로 다시 붙는다.
 * 다시 붙으면 구독마다 onResync를 불러 REST로 현재 상태를 다시 받게 한다(PRD 6장 실시간).
 * 핸드셰이크는 access_token 쿠키로 인증하므로, 끊긴 뒤에는 연결 전에 토큰을 재발급해 본다.
 */

import {refreshSession} from './api.js';
import {toast} from './ui.js';

const RECONNECT_MILLIS = 3000;
const subscriptions = new Map();
let client = null;
let connectedOnce = false;
let needsRefresh = false;
let nextId = 1;

/**
 * 토픽을 구독한다. 메시지는 서버 RealtimeMessage({type, data})로 넘어온다.
 * @param {string} destination 예: '/topic/meetings/1'
 * @param {(message: {type: string, data: any}) => void} onMessage 메시지 처리
 * @param {{onResync?: () => void}} options 재연결 뒤 상태를 다시 받을 함수
 * @returns {() => void} 구독 해제 함수
 */
export function subscribe(destination, onMessage, {onResync} = {}) {
	const id = nextId++;
	const entry = {destination, onMessage, onResync, handle: null};
	subscriptions.set(id, entry);
	const stomp = ensureClient();
	if (stomp.connected) {
		attach(stomp, entry);
	}
	return () => unsubscribe(id);
}

/**
 * 구독을 해제하고, 남은 구독이 없으면 연결을 닫는다.
 * @param {number} id 구독 번호
 */
function unsubscribe(id) {
	const entry = subscriptions.get(id);
	subscriptions.delete(id);
	entry?.handle?.unsubscribe();
	if (subscriptions.size === 0 && client) {
		client.deactivate();
		client = null;
		connectedOnce = false;
	}
}

/**
 * STOMP 클라이언트를 만들고 연결을 시작한다(이미 있으면 그대로 쓴다).
 * @returns {any} StompJs.Client
 */
function ensureClient() {
	if (client) {
		return client;
	}
	const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
	client = new window.StompJs.Client({
		brokerURL: `${scheme}://${window.location.host}/ws`,
		reconnectDelay: RECONNECT_MILLIS,
		beforeConnect: refreshIfNeeded,
		onConnect: handleConnect,
		onWebSocketClose: () => {
			needsRefresh = true;
		},
		onStompError: (frame) => toast(frame.headers.message || '실시간 연결에 문제가 생겼어요', 'error'),
	});
	client.activate();
	return client;
}

/**
 * 직전 연결이 끊겼으면 토큰부터 재발급한다(만료된 access_token이면 핸드셰이크가 401이라서).
 * @returns {Promise<void>}
 */
async function refreshIfNeeded() {
	if (needsRefresh) {
		needsRefresh = false;
		await refreshSession();
	}
}

/**
 * 연결되면 모든 구독을 다시 붙이고, 재연결이면 상태를 다시 받게 한다.
 */
function handleConnect() {
	const reconnected = connectedOnce;
	connectedOnce = true;
	for (const entry of subscriptions.values()) {
		attach(client, entry);
		if (reconnected && entry.onResync) {
			entry.onResync();
		}
	}
}

/**
 * 구독 하나를 STOMP에 붙인다.
 * @param {any} stomp StompJs.Client
 * @param {{destination: string, onMessage: Function, handle: any}} entry 구독
 */
function attach(stomp, entry) {
	entry.handle = stomp.subscribe(entry.destination, (frame) => {
		entry.onMessage(JSON.parse(frame.body));
	});
}
