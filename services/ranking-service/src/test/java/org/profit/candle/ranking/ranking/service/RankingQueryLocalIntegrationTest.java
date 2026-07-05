package org.profit.candle.ranking.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.profit.candle.ranking.ranking.cache.RedisRankingCache;
import org.profit.candle.ranking.ranking.repository.JdbcRankingQueryRepository;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_RANKING_QUERY_TEST", matches = "true")
class RankingQueryLocalIntegrationTest {

    private static final LocalDate TEST_DATE = LocalDate.of(9999, 12, 31);
    private static final UUID FIRST_USER = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID SECOND_USER = UUID.fromString("80000000-0000-4000-8000-000000000002");

    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redisTemplate;
    private LettuceConnectionFactory connectionFactory;

    /** 로컬 PostgreSQL·Redis 연결과 조회용 순위 두 건을 준비한다. */
    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                environment("RANKING_DB_URL", "jdbc:postgresql://localhost:5432/candle?currentSchema=ranking,public"),
                environment("RANKING_DB_USERNAME", "candle"),
                environment("RANKING_DB_PASSWORD", "candle"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        connectionFactory = new LettuceConnectionFactory(
                environment("RANKING_REDIS_HOST", "localhost"),
                Integer.parseInt(environment("RANKING_REDIS_PORT", "6379")));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        cleanUp();
        insertRanking(FIRST_USER, "first", 1, 12_000L, "12.5000", 8);
        insertRanking(SECOND_USER, "second", 2, 11_000L, "10.0000", 6);
        jdbcTemplate.update(
                "INSERT INTO ranking_runs (ranking_date, ranked_user_count) VALUES (?, 2)", TEST_DATE);
    }

    /** 확인 옵션이 없으면 DB·Redis 테스트 데이터를 삭제하고 연결을 종료한다. */
    @AfterEach
    void tearDown() {
        if (!Boolean.parseBoolean(environment("KEEP_LOCAL_RANKING_QUERY_TEST_DATA", "false"))) {
            cleanUp();
        }
        connectionFactory.destroy();
    }

    /** DB fallback 결과가 반환되고 Redis TOP·사용자 캐시가 생성되는지 검증한다. */
    @Test
    void queriesDatabaseAndBuildsRedisCache() {
        DefaultRankingQueryService service = new DefaultRankingQueryService(
                new JdbcRankingQueryRepository(jdbcTemplate),
                new RedisRankingCache(redisTemplate));

        var page = service.listRankings(20, "");
        var myRanking = service.getMyRanking(SECOND_USER);

        assertThat(page.rankings()).extracting(result -> result.position()).containsExactly(1, 2);
        assertThat(myRanking.position()).isEqualTo(2);
        assertThat(redisTemplate.opsForValue().get("ranking:latest-date"))
                .isEqualTo(TEST_DATE.toString());
        assertThat(redisTemplate.opsForList().size("ranking:" + TEST_DATE + ":top100"))
                .isEqualTo(2L);
        assertThat(redisTemplate.hasKey("ranking:" + TEST_DATE + ":user:" + SECOND_USER)).isTrue();
    }

    /** 테스트 참가자와 최종 순위를 PostgreSQL에 저장한다. */
    private void insertRanking(
            UUID userId,
            String nickname,
            int position,
            long totalAsset,
            String profitRate,
            int tradeCount) {
        jdbcTemplate.update("""
                INSERT INTO ranking_participants
                    (user_id, nickname, trade_count, user_status, account_status)
                VALUES (?, ?, ?, 'ACTIVE', 'ACTIVE')
                """, userId, nickname, tradeCount);
        jdbcTemplate.update("""
                INSERT INTO ranking_history
                    (user_id, ranking_position, total_asset, profit_rate, trade_count, ranking_date)
                VALUES (?, ?, ?, ?::numeric, ?, ?)
                """, userId, position, totalAsset, profitRate, tradeCount, TEST_DATE);
    }

    /** 테스트 날짜와 사용자에 해당하는 PostgreSQL·Redis 데이터를 정리한다. */
    private void cleanUp() {
        if (jdbcTemplate != null) {
            jdbcTemplate.update("DELETE FROM ranking_history WHERE ranking_date = ?", TEST_DATE);
            jdbcTemplate.update("DELETE FROM ranking_runs WHERE ranking_date = ?", TEST_DATE);
            jdbcTemplate.update(
                    "DELETE FROM ranking_participants WHERE user_id IN (?, ?)", FIRST_USER, SECOND_USER);
        }
        if (redisTemplate != null) {
            redisTemplate.delete("ranking:latest-date");
            redisTemplate.delete("ranking:" + TEST_DATE + ":top100");
            redisTemplate.delete("ranking:" + TEST_DATE + ":user:" + FIRST_USER);
            redisTemplate.delete("ranking:" + TEST_DATE + ":user:" + SECOND_USER);
        }
    }

    /** 환경변수가 없으면 로컬 개발 기본값을 반환한다. */
    private String environment(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
