/*
 * 장소 검색(P-1, 참고용). 카카오 로컬 결과를 보여 주기만 하고 저장하거나 입력칸을 채우지 않는다
 * (오픈 이슈 5, 카카오 데이터 무저장). 사용자가 보고 후보 폼에 직접 입력한다.
 */

import {api} from '../../api.js';
import {runAction} from '../../common.js';
import {html, raw} from '../../ui.js';

/**
 * 검색 영역을 그린다.
 * @param {HTMLElement} box 검색 영역
 */
export function mountPlaceSearch(box) {
	box.innerHTML = html`<details class="search-area"><summary>🔍 장소 검색(참고용)</summary>
		<form role="search"><input name="query" type="search" placeholder="예: 강남역 고깃집" maxlength="100" required>
			<button type="submit">검색</button></form>
		<p class="muted"><small>검색 결과는 참고만 할 수 있어요. 마음에 들면 후보 칸에 직접 적어 주세요.</small></p>
		<ul class="search-results list"></ul>
		<button type="button" class="secondary search-more" hidden>더 보기</button>
	</details>`;
	const area = box.querySelector('.search-area');
	const state = {query: '', page: 1};
	area.querySelector('form').addEventListener('submit', (event) => runAction(event, () => {
		Object.assign(state, {query: event.target.elements.query.value, page: 1});
		area.querySelector('.search-results').innerHTML = '';
		return loadPage(area, state);
	}));
	area.querySelector('.search-more').addEventListener('click', (event) => runAction(event, () => loadPage(area, state)));
}

/**
 * 검색 결과 한 페이지를 이어 붙인다.
 * @param {HTMLElement} area 검색 영역
 * @param {{query: string, page: number}} state 검색어와 다음 페이지
 * @returns {Promise<void>}
 */
async function loadPage(area, state) {
	const query = encodeURIComponent(state.query);
	const result = await api.get(`/api/places/search?query=${query}&page=${state.page}`);
	const items = result.places.map((place) => html`<li><div class="list-row"><span class="row-body">
		<span class="row-title">${place.name} ${raw(externalLink(place.placeUrl))}</span>
		<span class="row-sub">${place.categoryName ?? ''}</span>
		<span class="row-sub">${place.roadAddress || place.address || ''}</span></span></div></li>`);
	area.querySelector('.search-results').insertAdjacentHTML('beforeend',
		items.length ? items.join('') : html`<li class="empty">결과가 없어요</li>`);
	area.querySelector('.search-more').hidden = result.isEnd;
	state.page += 1;
}

/**
 * 카카오맵 장소 페이지 링크(카카오가 http로 줄 때도 있다). http(s)가 아니면 그리지 않는다.
 * @param {string|null} url 장소 페이지 URL
 * @returns {string} HTML
 */
function externalLink(url) {
	return url && /^https?:\/\//.test(url)
		? html`<span class="chip"><a href="${url}" target="_blank" rel="noopener noreferrer">카카오맵</a></span>` : '';
}
