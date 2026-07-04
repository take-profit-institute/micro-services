package org.profit.candle.news.naver.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NaverNewsSearchRequestTest {
    @Test
    void shouldNormalizeQueryAndSort() {
        NaverNewsSearchRequest request = new NaverNewsSearchRequest(" Samsung ", 3, 1, "DATE");

        assertThat(request.query()).isEqualTo("Samsung");
        assertThat(request.sort()).isEqualTo("date");
    }

    @Test
    void shouldUseDateSortWhenSortIsBlank() {
        NaverNewsSearchRequest request = new NaverNewsSearchRequest("Samsung", 3, 1, " ");

        assertThat(request.sort()).isEqualTo("date");
    }

    @Test
    void shouldRejectInvalidDisplay() {
        assertThatThrownBy(() -> new NaverNewsSearchRequest("Samsung", 0, 1, "date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("display must be between 1 and 100");

        assertThatThrownBy(() -> new NaverNewsSearchRequest("Samsung", 101, 1, "date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("display must be between 1 and 100");
    }

    @Test
    void shouldRejectInvalidStart() {
        assertThatThrownBy(() -> new NaverNewsSearchRequest("Samsung", 3, 0, "date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("start must be between 1 and 1000");

        assertThatThrownBy(() -> new NaverNewsSearchRequest("Samsung", 3, 1001, "date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("start must be between 1 and 1000");
    }

    @Test
    void shouldRejectInvalidSort() {
        assertThatThrownBy(() -> new NaverNewsSearchRequest("Samsung", 3, 1, "recent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sort must be sim or date");
    }
}
