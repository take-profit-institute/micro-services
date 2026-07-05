package org.profit.candle.ranking.ranking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.profit.candle.ranking.ranking.client.PortfolioSnapshotItem;
import org.profit.candle.ranking.ranking.client.PortfolioSnapshotPage;
import org.profit.candle.ranking.ranking.service.DefaultDailyRankingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_RANKING_DB_TEST", matches = "true")
class DailyRankingLocalIntegrationTest {

    private static final LocalDate TEST_DATE = LocalDate.of(2099, 12, 31);
    private static final UUID FIRST_USER = UUID.fromString("90000000-0000-4000-8000-000000000001");
    private static final UUID SECOND_USER = UUID.fromString("90000000-0000-4000-8000-000000000002");

    private JdbcTemplate jdbcTemplate;

    /** 로컬 Ranking DB 연결과 테스트 참가자를 준비한다. */
    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                environment("RANKING_DB_URL", "jdbc:postgresql://localhost:5432/candle?currentSchema=ranking,public"),
                environment("RANKING_DB_USERNAME", "candle"),
                environment("RANKING_DB_PASSWORD", "candle"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        cleanUp();
        insertParticipant(FIRST_USER, "first", 5);
        insertParticipant(SECOND_USER, "second", 8);
    }

    /** 별도 확인 옵션이 없으면 테스트가 만든 일별 결과와 참가자를 모두 삭제한다. */
    @AfterEach
    void tearDown() {
        if (!Boolean.parseBoolean(environment("KEEP_LOCAL_RANKING_DB_TEST_DATA", "false"))) {
            cleanUp();
        }
    }

    /** Fake #105 입력이 실제 PostgreSQL 세 테이블에 순위와 완료 기록으로 저장되는지 검증한다. */
    @Test
    void savesDailyRankingToPostgreSql() {
        JdbcDailyRankingRepository repository = new JdbcDailyRankingRepository(jdbcTemplate);
        DefaultDailyRankingService service = new DefaultDailyRankingService(
                (snapshotDate, pageToken, pageSize) -> new PortfolioSnapshotPage(List.of(
                        new PortfolioSnapshotItem(FIRST_USER, 100_000L, new BigDecimal("5.0000")),
                        new PortfolioSnapshotItem(SECOND_USER, 200_000L, new BigDecimal("8.0000"))), ""),
                repository);

        var result = service.finalizeDailyRanking(TEST_DATE);

        assertThat(result.rankedUserCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                        SELECT user_id FROM ranking_history
                        WHERE ranking_date = ? ORDER BY ranking_position
                        """, UUID.class, TEST_DATE))
                .containsExactly(SECOND_USER, FIRST_USER);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT ranked_user_count FROM ranking_runs WHERE ranking_date = ?
                        """, Integer.class, TEST_DATE))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM ranking_snapshot WHERE snapshot_date = ?
                        """, Long.class, TEST_DATE))
                .isEqualTo(2L);
    }

    /** ACTIVE 상태의 테스트 참가자를 Ranking DB에 추가한다. */
    private void insertParticipant(UUID userId, String nickname, int tradeCount) {
        jdbcTemplate.update("""
                INSERT INTO ranking_participants
                    (user_id, nickname, trade_count, user_status, account_status)
                VALUES (?, ?, ?, 'ACTIVE', 'ACTIVE')
                """, userId, nickname, tradeCount);
    }

    /** 테스트 날짜와 테스트 사용자에 해당하는 데이터를 정리한다. */
    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM ranking_history WHERE ranking_date = ?", TEST_DATE);
        jdbcTemplate.update("DELETE FROM ranking_snapshot WHERE snapshot_date = ?", TEST_DATE);
        jdbcTemplate.update("DELETE FROM ranking_runs WHERE ranking_date = ?", TEST_DATE);
        jdbcTemplate.update(
                "DELETE FROM ranking_participants WHERE user_id IN (?, ?)", FIRST_USER, SECOND_USER);
    }

    /** 환경변수가 없으면 로컬 개발 기본값을 반환한다. */
    private String environment(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
