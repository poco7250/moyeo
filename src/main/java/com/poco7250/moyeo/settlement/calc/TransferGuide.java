package com.poco7250.moyeo.settlement.calc;

/**
 * 송금 안내 1건. 실제 송금은 앱 밖에서 한다.
 */
public record TransferGuide(long fromMemberId, long toMemberId, long amount) {
}
