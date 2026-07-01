package org.profit.candle.stock.catalog.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StockEntityTest {

    @Test
    void constructor_setsRequiredFieldsAndDefaults() {
        StockEntity stock = new StockEntity("005930", "삼성전자", "KOSPI");

        assertThat(stock.stockCode()).isEqualTo("005930");
        assertThat(stock.stockName()).isEqualTo("삼성전자");
        assertThat(stock.marketType()).isEqualTo("KOSPI");
        assertThat(stock.listingStatus()).isEqualTo("LISTED");
        assertThat(stock.dataSource()).isEqualTo("SEED");
    }

    @Test
    void applyReferenceData_replacesNonNullFieldsAndMarksSynced() {
        StockEntity stock = new StockEntity("005930", "삼성전자", "KOSPI");
        LocalDate listedAt = LocalDate.of(1975, 6, 11);

        stock.applyReferenceData(
                "삼성전자보통주",
                "KOSPI",
                "전기전자",
                450_000_000_000_000L,
                5_969_782_550L,
                listedAt,
                "SUSPENDED",
                "KIWOOM");

        assertThat(stock.stockName()).isEqualTo("삼성전자보통주");
        assertThat(stock.sector()).isEqualTo("전기전자");
        assertThat(stock.marketCap()).isEqualTo(450_000_000_000_000L);
        assertThat(stock.sharesOutstanding()).isEqualTo(5_969_782_550L);
        assertThat(stock.listedAt()).isEqualTo(listedAt);
        assertThat(stock.listingStatus()).isEqualTo("SUSPENDED");
        assertThat(stock.dataSource()).isEqualTo("KIWOOM");
        assertThat(stock.syncedAt()).isNotNull();
    }

    @Test
    void applyReferenceData_keepsExistingValuesWhenInputsAreNull() {
        StockEntity stock = new StockEntity("005930", "삼성전자", "KOSPI");
        stock.applyReferenceData("삼성전자", "KOSPI", "전기전자", 1L, 2L,
                LocalDate.of(1975, 6, 11), "LISTED", "KIWOOM");

        stock.applyReferenceData(null, null, null, null, null, null, null, "KIWOOM");

        assertThat(stock.stockName()).isEqualTo("삼성전자");
        assertThat(stock.marketType()).isEqualTo("KOSPI");
        assertThat(stock.sector()).isEqualTo("전기전자");
        assertThat(stock.marketCap()).isEqualTo(1L);
        assertThat(stock.sharesOutstanding()).isEqualTo(2L);
        assertThat(stock.listingStatus()).isEqualTo("LISTED");
        assertThat(stock.dataSource()).isEqualTo("KIWOOM");
    }
}
