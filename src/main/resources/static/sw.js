/*
 * 웹 푸시 서비스 워커(Task 056 서버 푸시 수신). 서버 페이로드는 {type, title, body, url, tag} JSON이다.
 * 알림을 누르면 url(모임 링크)을 연다. 이미 열린 창이 있으면 그 창으로 간다.
 */

self.addEventListener('push', (event) => {
	const payload = readPayload(event);
	event.waitUntil(self.registration.showNotification(payload.title || '모여', {
		body: payload.body || '',
		tag: payload.tag || undefined,
		data: {url: payload.url || '/'},
	}));
});

self.addEventListener('notificationclick', (event) => {
	event.notification.close();
	const url = new URL(event.notification.data?.url || '/', self.location.origin).href;
	event.waitUntil(focusOrOpen(url));
});

/**
 * 푸시 데이터를 JSON으로 읽는다. 깨진 데이터면 빈 객체.
 * @param {PushEvent} event 푸시 이벤트
 * @returns {{title?: string, body?: string, url?: string, tag?: string}} 페이로드
 */
function readPayload(event) {
	try {
		return event.data ? event.data.json() : {};
	} catch {
		return {};
	}
}

/**
 * 같은 주소의 창이 열려 있으면 포커스하고, 없으면 새 창을 연다.
 * @param {string} url 열 주소
 * @returns {Promise<unknown>}
 */
async function focusOrOpen(url) {
	const windows = await self.clients.matchAll({type: 'window', includeUncontrolled: true});
	const opened = windows.find((client) => client.url === url);
	return opened ? opened.focus() : self.clients.openWindow(url);
}
