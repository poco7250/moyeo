package com.poco7250.moyeo.settlement.dto;

import com.poco7250.moyeo.settlement.domain.ChangeLog;
import java.util.List;
import org.springframework.data.domain.Slice;

/**
 * 변경 로그 한 페이지 응답. 오래된 순이며 전체 개수 대신 다음 페이지 유무만 준다.
 *
 * @param page 0부터 시작하는 페이지 번호
 */
public record ChangeLogPageResponse(List<ChangeLogResponse> items, int page, int size, boolean hasNext) {

	/**
	 * 조회한 페이지를 응답으로 변환한다.
	 */
	public static ChangeLogPageResponse from(Slice<ChangeLog> slice) {
		return new ChangeLogPageResponse(slice.getContent().stream().map(ChangeLogResponse::from).toList(),
				slice.getNumber(), slice.getSize(), slice.hasNext());
	}
}
