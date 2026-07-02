package org.profit.candle.market.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockTest {

    @Test
    void auditFields_useInstantAndDeleteUpdatesTimestamp() {
        Stock stock = new Stock("005930", "삼성전자", "KOSPI");

        assertThat(stock.createdAt()).isInstanceOf(Instant.class);
        assertThat(stock.updatedAt()).isInstanceOf(Instant.class);

        stock.delete();

        assertThat(stock.deletedAt()).isNotNull();
        assertThat(stock.updatedAt()).isEqualTo(stock.deletedAt());
    }
}
