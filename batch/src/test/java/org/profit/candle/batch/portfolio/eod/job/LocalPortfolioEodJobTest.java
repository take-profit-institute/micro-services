package org.profit.candle.batch.portfolio.eod.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.client.CashBalanceClient;
import org.profit.candle.batch.portfolio.eod.client.ClosingPriceClient;
import org.profit.candle.batch.portfolio.eod.client.PortfolioSnapshotClient;
import org.profit.candle.batch.portfolio.eod.client.SeedCapitalProvider;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.portfolio.eod.idempotency.SnapshotIdempotencyKeyFactory;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.batch.portfolio.eod.repository.ClosingPriceStageRepository;
import org.profit.candle.batch.support.parameter.JobParameterFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:portfolio-eod;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.locations=classpath:migration,classpath:local-migration",
        "spring.batch.job.enabled=false",
        "batch.schedule.smoke.enabled=false",
        "batch.schedule.portfolio-eod.enabled=true",
        "batch.schedule.portfolio-eod.cron=0 0 0 1 1 *",
        "batch.schedule.portfolio-eod.chunk-size=2",
        "batch.schedule.portfolio-eod.symbol-batch-size=2"
})
@ActiveProfiles("local-eod")
@Import(LocalPortfolioEodJobTest.LocalEodFakeConfiguration.class)
class LocalPortfolioEodJobTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 6, 30);

    private final JobOperator jobOperator;
    private final Job portfolioEodJob;
    private final JobParameterFactory parameterFactory;
    private final SnapshotIdempotencyKeyFactory idempotencyKeyFactory;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LocalPortfolioEodJobTest(
            JobOperator jobOperator,
            @Qualifier(PortfolioEodJobConfiguration.JOB_NAME) Job portfolioEodJob,
            JobParameterFactory parameterFactory,
            SnapshotIdempotencyKeyFactory idempotencyKeyFactory,
            JdbcTemplate jdbcTemplate
    ) {
        this.jobOperator = jobOperator;
        this.portfolioEodJob = portfolioEodJob;
        this.parameterFactory = parameterFactory;
        this.idempotencyKeyFactory = idempotencyKeyFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void shouldRunWholeJobAndStoreSnapshotsWithFakeServiceData() throws Exception {
        JobExecution execution = jobOperator.start(
                portfolioEodJob,
                parameterFactory.createDailyParameters(
                        PortfolioEodJobConfiguration.JOB_NAME,
                        BUSINESS_DATE
                )
        );

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(loadCurrentSnapshots()).containsExactly(
                new SnapshotRow(
                        "00000000-0000-0000-0000-000000000001",
                        570_000,
                        470_000,
                        20_000,
                        "14.00",
                        idempotencyKeyFactory.create(
                                BUSINESS_DATE,
                                "00000000-0000-0000-0000-000000000001"
                        )
                ),
                new SnapshotRow(
                        "00000000-0000-0000-0000-000000000002",
                        400_000,
                        200_000,
                        0,
                        "0.00",
                        idempotencyKeyFactory.create(
                                BUSINESS_DATE,
                                "00000000-0000-0000-0000-000000000002"
                        )
                )
        );
        Integer stagedPriceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch_portfolio_eod_closing_prices",
                Integer.class
        );
        assertThat(stagedPriceCount).isEqualTo(3);
    }

    private List<SnapshotRow> loadCurrentSnapshots() {
        return jdbcTemplate.query(
                """
                SELECT user_id, total_asset, stock_value, daily_profit,
                       cumulative_return_rate, idempotency_key
                FROM local_eod_snapshots
                WHERE snapshot_date = ?
                ORDER BY user_id
                """,
                (resultSet, rowNumber) -> new SnapshotRow(
                        resultSet.getString("user_id"),
                        resultSet.getLong("total_asset"),
                        resultSet.getLong("stock_value"),
                        resultSet.getLong("daily_profit"),
                        resultSet.getString("cumulative_return_rate"),
                        resultSet.getString("idempotency_key")
                ),
                BUSINESS_DATE
        );
    }

    private record SnapshotRow(
            String userId,
            long totalAsset,
            long stockValue,
            long dailyProfit,
            String cumulativeReturnRate,
            String idempotencyKey
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalEodFakeConfiguration {

        @Bean
        LocalEodFakeStore localEodFakeStore(JdbcTemplate jdbcTemplate) {
            return new LocalEodFakeStore(jdbcTemplate);
        }

        @Bean
        @Primary
        ClosingPriceStageRepository localClosingPriceStageRepository(
                JdbcTemplate jdbcTemplate
        ) {
            return new H2ClosingPriceStageRepository(jdbcTemplate);
        }
    }

    /**
     * TEST ONLY: 운영 PostgreSQL의 ON CONFLICT 대신 H2의 MERGE 문법을 사용한다.
     */
    static class H2ClosingPriceStageRepository extends ClosingPriceStageRepository {

        private final JdbcTemplate jdbcTemplate;

        H2ClosingPriceStageRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void upsertAll(
                long jobInstanceId,
                LocalDate businessDate,
                List<ClosingPrice> prices
        ) {
            prices.forEach(price -> jdbcTemplate.update(
                    """
                    MERGE INTO batch_portfolio_eod_closing_prices (
                        job_instance_id, business_date, symbol, closing_price, quoted_at
                    ) KEY (job_instance_id, symbol) VALUES (?, ?, ?, ?, ?)
                    """,
                    jobInstanceId,
                    businessDate,
                    price.symbol(),
                    price.price(),
                    Timestamp.from(price.quotedAt())
            ));
        }
    }

    /**
     * TEST ONLY: 외부 계약 완료 후 운영에서는 각 gRPC adapter가 이 interface들을 구현한다.
     */
    static class LocalEodFakeStore implements
            SnapshotTargetClient,
            CashBalanceClient,
            ClosingPriceClient,
            SeedCapitalProvider,
            PortfolioSnapshotClient {

        private static final ZoneId KST = ZoneId.of("Asia/Seoul");

        private final JdbcTemplate jdbcTemplate;

        LocalEodFakeStore(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public SnapshotTarget.Page loadTargets(
                LocalDate businessDate,
                String pageToken,
                int pageSize
        ) {
            List<String> userIds = jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT user_id
                    FROM local_eod_holdings
                    WHERE active = TRUE AND quantity > 0 AND user_id > ?
                    ORDER BY user_id
                    LIMIT ?
                    """,
                    String.class,
                    pageToken,
                    pageSize
            );
            List<SnapshotTarget> targets = userIds.stream()
                    .map(this::loadTarget)
                    .toList();
            String nextPageToken = userIds.size() == pageSize
                    ? userIds.getLast()
                    : "";
            return new SnapshotTarget.Page(targets, nextPageToken);
        }

        @Override
        public long getCash(String userId) {
            return requiredLong(
                    "SELECT cash FROM local_eod_accounts WHERE user_id = ?",
                    userId
            );
        }

        @Override
        public List<ClosingPrice> loadClosingPrices(
                LocalDate businessDate,
                List<String> symbols
        ) {
            return symbols.stream()
                    .map(symbol -> new ClosingPrice(
                            symbol,
                            requiredLong(
                                    """
                                    SELECT closing_price
                                    FROM local_eod_holdings
                                    WHERE symbol = ? AND active = TRUE
                                    LIMIT 1
                                    """,
                                    symbol
                            ),
                            businessDate.atTime(LocalTime.of(15, 30)).atZone(KST).toInstant()
                    ))
                    .toList();
        }

        @Override
        public long getSeedCapital(String userId, LocalDate businessDate) {
            return requiredLong(
                    "SELECT seed_capital FROM local_eod_accounts WHERE user_id = ?",
                    userId
            );
        }

        @Override
        public void recordDailySnapshot(SnapshotCommand command) {
            long previousTotalAsset = loadPreviousTotalAsset(command);
            long dailyProfit = previousTotalAsset == 0
                    ? 0
                    : command.totalAsset() - previousTotalAsset;
            String cumulativeReturnRate = BigDecimal.valueOf(
                            command.totalAsset() - command.seedCapital()
                    )
                    .multiply(BigDecimal.valueOf(100))
                    .divide(
                            BigDecimal.valueOf(command.seedCapital()),
                            2,
                            RoundingMode.HALF_UP
                    )
                    .toPlainString();

            jdbcTemplate.update(
                    """
                    MERGE INTO local_eod_snapshots (
                        user_id, snapshot_date, total_asset, stock_value,
                        daily_profit, cumulative_return_rate, idempotency_key
                    ) KEY (user_id, snapshot_date) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    command.userId(),
                    command.businessDate(),
                    command.totalAsset(),
                    command.stockValue(),
                    dailyProfit,
                    cumulativeReturnRate,
                    command.idempotencyKey()
            );
        }

        private SnapshotTarget loadTarget(String userId) {
            List<SnapshotTarget.Holding> holdings = jdbcTemplate.query(
                    """
                    SELECT symbol, quantity, average_price
                    FROM local_eod_holdings
                    WHERE user_id = ? AND active = TRUE AND quantity > 0
                    ORDER BY symbol
                    """,
                    (resultSet, rowNumber) -> new SnapshotTarget.Holding(
                            resultSet.getString("symbol"),
                            resultSet.getLong("quantity"),
                            resultSet.getLong("average_price")
                    ),
                    userId
            );
            return new SnapshotTarget(userId, holdings);
        }

        private long loadPreviousTotalAsset(SnapshotCommand command) {
            List<Long> values = jdbcTemplate.query(
                    """
                    SELECT total_asset
                    FROM local_eod_snapshots
                    WHERE user_id = ? AND snapshot_date < ?
                    ORDER BY snapshot_date DESC
                    LIMIT 1
                    """,
                    (resultSet, rowNumber) -> resultSet.getLong("total_asset"),
                    command.userId(),
                    command.businessDate()
            );
            return values.isEmpty() ? 0 : values.getFirst();
        }

        private long requiredLong(String sql, String argument) {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, argument);
            if (value == null) {
                throw new IllegalStateException("Required local EOD fixture is missing.");
            }
            return value;
        }
    }
}
