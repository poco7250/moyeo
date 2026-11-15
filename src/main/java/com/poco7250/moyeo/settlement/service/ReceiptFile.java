package com.poco7250.moyeo.settlement.service;

import org.springframework.core.io.Resource;

/**
 * 내려줄 영수증 사진 파일과 응답 헤더에 쓸 값.
 *
 * @param etag 파일마다 다른 값(저장소 파일 이름). 교체되면 바뀐다
 */
public record ReceiptFile(Resource resource, String contentType, long size, String etag) {
}
