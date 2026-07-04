package org.profit.candle.market.dto;

import java.time.Instant;

/** 당일 틱 스냅샷 1건 — 그래프용 (체결가 + 체결시각). */
public record IntradayTickResult(long price, Instant time) {
}
