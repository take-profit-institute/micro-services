package org.profit.candle.market.ranking.service;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.ranking.exception.RankingException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankingCacheServiceTest {
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private final RankingCacheService rankingCacheService = new RankingCacheService(redisTemplate);

    @Test
    void parseLongRemovesPlusAndComma() {
        long result = rankingCacheService.parseLong("+1,234");

        assertEquals(1234L, result);
    }

    @Test
    void parseLongReturnsZeroWhenBlank() {
        long result = rankingCacheService.parseLong("");

        assertEquals(0L, result);
    }

    @Test
    void parseLongAbsReturnsAbsoluteValue() {
        long result = rankingCacheService.parseLongAbs("-1500");

        assertEquals(1500L, result);
    }

    @Test
    void parseDoubleRemovesPlusPercentAndComma() {
        double result = rankingCacheService.parseDouble("+12.34%");

        assertEquals(12.34, result);
    }

    @Test
    void validateResponseDoesNotThrowWhenValid() {
        assertDoesNotThrow(() ->
                rankingCacheService.validateResponse(List.of("item"), 0)
        );
    }

    @Test
    void validateResponseThrowsWhenReturnCodeIsNotZero() {
        assertThrows(RankingException.class, () ->
                rankingCacheService.validateResponse(List.of("item"), 1)
        );
    }

    @Test
    void validateResponseThrowsWhenItemsIsNull() {
        assertThrows(RankingException.class, () ->
                rankingCacheService.validateResponse(null, 0)
        );
    }

    @Test
    void validateResponseThrowsWhenItemsIsEmpty() {
        assertThrows(RankingException.class, () ->
                rankingCacheService.validateResponse(List.of(), 0)
        );
    }

}
