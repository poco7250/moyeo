/*
 * 웹 푸시 구독(Task 056 서버 API 사용). 서비스 워커 /sw.js를 등록하고
 * 서버 VAPID 공개키로 구독해 endpoint·키를 서버에 등록한다. https 또는 localhost에서만 동작한다.
 */

import {ApiError, api} from './api.js';

const WORKER_URL = '/sw.js';

/**
 * 이 브라우저가 웹 푸시를 쓸 수 있는지 확인한다.
 * @returns {boolean} 지원 여부
 */
export function pushSupported() {
	return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

/**
 * 서버 VAPID 공개키를 받는다. 서버에 키가 없으면(503 PUSH_NOT_CONFIGURED) null.
 * @returns {Promise<string|null>} base64url 공개키
 */
export async function serverPublicKey() {
	try {
		return (await api.get('/api/push/vapid-public-key')).publicKey;
	} catch (error) {
		if (error instanceof ApiError && error.status === 503) {
			return null;
		}
		throw error;
	}
}

/**
 * 이 기기의 지금 구독을 돌려준다(없으면 null).
 * @returns {Promise<PushSubscription|null>} 구독
 */
export async function currentSubscription() {
	const registration = await navigator.serviceWorker.register(WORKER_URL);
	return registration.pushManager.getSubscription();
}

/**
 * 알림 권한을 받고 구독해 서버에 등록한다.
 * @param {string} publicKey 서버 VAPID 공개키(base64url)
 * @returns {Promise<void>}
 */
export async function subscribePush(publicKey) {
	if (await Notification.requestPermission() !== 'granted') {
		throw new Error('알림 권한이 없어요. 브라우저 설정에서 알림을 허용해 주세요.');
	}
	const registration = await navigator.serviceWorker.register(WORKER_URL);
	const subscription = await registration.pushManager.subscribe({
		userVisibleOnly: true,
		applicationServerKey: base64UrlToBytes(publicKey),
	});
	const {endpoint, keys} = subscription.toJSON();
	await api.post('/api/members/me/push-subscriptions', {endpoint, keys: {p256dh: keys.p256dh, auth: keys.auth}});
}

/**
 * 서버 등록을 지우고 이 기기 구독을 해제한다.
 * @param {PushSubscription} subscription 지금 구독
 * @returns {Promise<void>}
 */
export async function unsubscribePush(subscription) {
	await api.delete('/api/members/me/push-subscriptions', {body: {endpoint: subscription.endpoint}});
	await subscription.unsubscribe();
}

/**
 * base64url 문자열을 바이트 배열로 바꾼다(applicationServerKey 형식).
 * @param {string} value base64url
 * @returns {Uint8Array} 바이트
 */
function base64UrlToBytes(value) {
	const base64 = (value + '='.repeat((4 - (value.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
	return Uint8Array.from(window.atob(base64), (char) => char.charCodeAt(0));
}
