package com.poco7250.moyeo.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 영수증 사진 저장소 설정(receipt.storage.*).
 *
 * @param localBaseDir 로컬 디스크 저장 위치. 상대 경로면 실행 위치(프로젝트 루트) 기준이다
 */
@ConfigurationProperties(prefix = "receipt.storage")
public record ReceiptStorageProperties(@DefaultValue("./data/receipts") String localBaseDir) {
}
