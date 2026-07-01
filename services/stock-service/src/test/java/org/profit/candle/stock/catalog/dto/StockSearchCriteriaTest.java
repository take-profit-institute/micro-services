package org.profit.candle.stock.catalog.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockSearchCriteriaTest {

    @Test
    void constructor_normalizesBlankStringsToNull() {
        StockSearchCriteria criteria = new StockSearchCriteria(" ", "", "\t", null);

        assertThat(criteria.query()).isNull();
        assertThat(criteria.market()).isNull();
        assertThat(criteria.sector()).isNull();
        assertThat(criteria.status()).isNull();
    }

    @Test
    void constructor_keepsNonBlankValues() {
        StockSearchCriteria criteria = new StockSearchCriteria("삼성", "KOSPI", "전기전자", "LISTED");

        assertThat(criteria.query()).isEqualTo("삼성");
        assertThat(criteria.market()).isEqualTo("KOSPI");
        assertThat(criteria.sector()).isEqualTo("전기전자");
        assertThat(criteria.status()).isEqualTo("LISTED");
    }
}
