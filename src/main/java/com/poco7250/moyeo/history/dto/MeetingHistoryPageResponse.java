package com.poco7250.moyeo.history.dto;

import java.util.List;
import org.springframework.data.domain.Slice;

/**
 * 모임 히스토리 한 페이지 응답. 최근 모임 순이며 전체 개수 대신 다음 페이지 유무만 준다.
 *
 * @param page 0부터 시작하는 페이지 번호
 */
public record MeetingHistoryPageResponse(List<MeetingHistoryResponse> items, int page, int size, boolean hasNext) {

	/**
	 * 조회한 모임 페이지와 조립한 항목으로 응답을 만든다.
	 */
	public static MeetingHistoryPageResponse of(Slice<?> slice, List<MeetingHistoryResponse> items) {
		return new MeetingHistoryPageResponse(items, slice.getNumber(), slice.getSize(), slice.hasNext());
	}
}
