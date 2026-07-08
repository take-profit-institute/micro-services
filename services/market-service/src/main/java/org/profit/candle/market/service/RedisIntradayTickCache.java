package org.profit.candle.market.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.market.dto.IntradayTickResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisIntradayTickCache implements IntradayTickCache {

    private static final String KEY_PREFIX = "market:intraday:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Optional<List<IntradayTickResult>> get(String symbol) {
        try {
            String json = redisTemplate.opsForValue().get(key(symbol));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<Entry> entries = objectMapper.readValue(json, new TypeReference<>() {});
            return Optional.of(entries.stream()
                    .map(entry -> new IntradayTickResult(entry.price(), Instant.parse(entry.time())))
                    .toList());
        } catch (RuntimeException exception) {
            log.warn("Failed to read intraday ticks cache for {}", symbol, exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(String symbol, List<IntradayTickResult> ticks, Duration ttl) {
        if (ticks.isEmpty()) {
            return;
        }
        try {
            List<Entry> entries = ticks.stream()
                    .map(tick -> new Entry(tick.price(), tick.time().toString()))
                    .toList();
            redisTemplate.opsForValue().set(key(symbol), objectMapper.writeValueAsString(entries), ttl);
        } catch (RuntimeException exception) {
            log.warn("Failed to write intraday ticks cache for {}", symbol, exception);
        }
    }

    private static String key(String symbol) {
        return KEY_PREFIX + symbol;
    }

    private record Entry(long price, String time) {
    }
}
