package org.profit.candle.ranking.ranking.cache;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.ranking.v1.RankingEntry;
import org.profit.candle.ranking.ranking.dto.RankingResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisRankingCache implements RankingCache {

    private static final String LATEST_DATE_KEY = "ranking:latest-date";
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;

    /** Redis 문자열을 마지막 완료일로 변환한다. */
    @Override
    public Optional<LocalDate> findLatestDate() {
        return Optional.ofNullable(redisTemplate.opsForValue().get(LATEST_DATE_KEY)).map(LocalDate::parse);
    }

    /** 마지막 완료일을 하루 동안 캐시한다. */
    @Override
    public void putLatestDate(LocalDate rankingDate) {
        redisTemplate.opsForValue().set(LATEST_DATE_KEY, rankingDate.toString(), TTL);
    }

    /** protobuf Base64 목록을 TOP 100 조회 DTO로 복원한다. */
    @Override
    public Optional<List<RankingResult>> findTopRankings(LocalDate rankingDate) {
        List<String> values = redisTemplate.opsForList().range(topKey(rankingDate), 0, -1);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        List<RankingResult> rankings = new ArrayList<>(values.size());
        values.forEach(value -> rankings.add(decode(value)));
        return Optional.of(List.copyOf(rankings));
    }

    /** TOP 100을 protobuf Base64 목록으로 저장한다. */
    @Override
    public void putTopRankings(LocalDate rankingDate, List<RankingResult> rankings) {
        String key = topKey(rankingDate);
        redisTemplate.delete(key);
        if (!rankings.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, rankings.stream().map(this::encode).toList());
            redisTemplate.expire(key, TTL);
        }
    }

    /** 사용자별 protobuf 캐시를 복원한다. */
    @Override
    public Optional<RankingResult> findUserRanking(LocalDate rankingDate, UUID userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(userKey(rankingDate, userId)))
                .map(this::decode);
    }

    /** 사용자별 순위를 하루 동안 저장한다. */
    @Override
    public void putUserRanking(RankingResult ranking) {
        redisTemplate.opsForValue().set(
                userKey(ranking.rankingDate(), ranking.userId()), encode(ranking), TTL);
    }

    /** 조회 DTO를 protobuf로 바꿔 Base64 문자열로 저장한다. */
    private String encode(RankingResult ranking) {
        RankingEntry entry = RankingEntry.newBuilder()
                .setRank(ranking.position())
                .setUserId(ranking.userId().toString())
                .setNickname(ranking.nickname())
                .setReturnRate(ranking.profitRate().toPlainString())
                .setTotalAsset(ranking.totalAsset())
                .setTradeCount(ranking.tradeCount())
                .setRankingDate(ranking.rankingDate().toString())
                .build();
        return Base64.getEncoder().encodeToString(entry.toByteArray());
    }

    /** Base64 protobuf를 조회 DTO로 변환한다. */
    private RankingResult decode(String value) {
        try {
            RankingEntry entry = RankingEntry.parseFrom(Base64.getDecoder().decode(value));
            return new RankingResult(
                    Math.toIntExact(entry.getRank()),
                    UUID.fromString(entry.getUserId()),
                    entry.getNickname(),
                    entry.getTotalAsset(),
                    new java.math.BigDecimal(entry.getReturnRate()),
                    entry.getTradeCount(),
                    LocalDate.parse(entry.getRankingDate()));
        } catch (Exception exception) {
            throw new IllegalStateException("Ranking cache payload is invalid", exception);
        }
    }

    /** 날짜별 TOP 100 Redis key를 만든다. */
    private String topKey(LocalDate rankingDate) {
        return "ranking:" + rankingDate + ":top100";
    }

    /** 날짜·사용자별 Redis key를 만든다. */
    private String userKey(LocalDate rankingDate, UUID userId) {
        return "ranking:" + rankingDate + ":user:" + userId;
    }
}
