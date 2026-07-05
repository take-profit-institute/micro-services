package org.profit.candle.ranking.support.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Context;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.profit.candle.proto.common.v1.CommandMetadata;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingRequest;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_RANKING_COMMAND_DB_TEST", matches = "true")
class RankingCommandLocalIntegrationTest {

    private static final String ACTOR = "ranking-local-test";
    private static final String OPERATION = "candle.ranking.v1.RankingService/FinalizeDailyRanking";
    private static final String KEY = "33333333-3333-4333-8333-333333333333";
    private static final String RANKING_DATE = "2099-12-30";

    private JdbcTemplate jdbcTemplate;

    /** 로컬 Ranking DB와 실제 transaction manager를 준비한다. */
    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                environment("RANKING_DB_URL", "jdbc:postgresql://localhost:5432/candle?currentSchema=ranking,public"),
                environment("RANKING_DB_USERNAME", "candle"),
                environment("RANKING_DB_PASSWORD", "candle"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        cleanUp();
    }

    /** 확인 옵션이 없으면 테스트가 만든 멱등성·Outbox 행을 삭제한다. */
    @AfterEach
    void tearDown() {
        if (!Boolean.parseBoolean(environment("KEEP_LOCAL_RANKING_COMMAND_DB_TEST_DATA", "false"))) {
            cleanUp();
        }
    }

    /** 같은 요청을 두 번 실행해 실제 DB에는 성공 응답과 Outbox가 한 건씩만 저장되는지 검증한다. */
    @Test
    void storesOneIdempotencyRecordAndOneOutboxEvent() throws Exception {
        JdbcRankingCommandRepository repository = new JdbcRankingCommandRepository(jdbcTemplate);
        DriverManagerDataSource dataSource = (DriverManagerDataSource) jdbcTemplate.getDataSource();
        RankingIdempotencyExecutor executor = new RankingIdempotencyExecutor(
                repository,
                new RequestHasher(),
                new DataSourceTransactionManager(dataSource),
                Clock.fixed(Instant.parse("2099-12-30T06:30:00Z"), ZoneOffset.UTC));
        FinalizeDailyRankingRequest request = FinalizeDailyRankingRequest.newBuilder()
                .setRankingDate(RANKING_DATE)
                .setCommandMetadata(CommandMetadata.newBuilder().setIdempotencyKey(KEY))
                .build();
        FinalizeDailyRankingResponse expected = FinalizeDailyRankingResponse.newBuilder()
                .setRankingDate(RANKING_DATE)
                .setRankedUserCount(2)
                .build();
        AtomicInteger executions = new AtomicInteger();
        IdempotencyContext idempotencyContext = new IdempotencyContext(ACTOR, OPERATION, KEY);

        Context.current().withValue(IdempotencyContext.CONTEXT_KEY, idempotencyContext).call(() -> {
            executor.execute(request, () -> {
                executions.incrementAndGet();
                return expected;
            });
            executor.execute(request, () -> {
                executions.incrementAndGet();
                return expected;
            });
            return null;
        });

        assertThat(executions).hasValue(1);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM ranking_idempotency_records
                        WHERE actor_id = ? AND operation = ? AND idempotency_key = ?
                        """, Long.class, ACTOR, OPERATION, KEY))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM ranking_outbox_events
                        WHERE event_type = 'DailyRankingFinalized' AND aggregate_id = ?
                        """, Long.class, RANKING_DATE))
                .isEqualTo(1L);
    }

    /** 테스트 scope로 저장된 멱등성·Outbox 행을 정리한다. */
    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM ranking_outbox_events WHERE event_type = 'DailyRankingFinalized' AND aggregate_id = ?",
                RANKING_DATE);
        jdbcTemplate.update("""
                DELETE FROM ranking_idempotency_records
                WHERE actor_id = ? AND operation = ? AND idempotency_key = ?
                """, ACTOR, OPERATION, KEY);
    }

    /** 환경변수가 없으면 로컬 개발 기본값을 반환한다. */
    private String environment(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
